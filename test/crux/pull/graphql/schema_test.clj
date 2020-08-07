;; Copyright © 2020, JUXT LTD.

(ns crux.pull.graphql.schema-test
  (:require
   [crux.api :as crux]
   [crux.pull.alpha.graphql :as graphql]
   [crux.pull.eql-graphql-test :refer [introspection-query]]
   [cheshire.core :as json]
   [clojure.test :refer [deftest is are testing]]
   [flatland.ordered.map :refer [ordered-map]]))

(comment
  (do

    (defn validate-schema [schema]
      (when-not (get schema "types")
        (throw
         (ex-info
          "Schema validation error: types is required"
          {:schema schema})))

      (when-not (get schema "queryType")
        (throw
         (ex-info
          "Schema validation error: queryType is required"
          {:schema schema})))

      (when-not (get schema "directives")
        (throw
         (ex-info
          "Schema validation error: directives is required"
          {:schema schema})))

      schema)

    (defn
      ^{:crux.graphql.spec-ref/version "June2018"
        :crux.graphql.spec-ref/section "6.3.2"
        :crux.graphql.spec-ref/algorithm "CollectFields"}
      collect-fields
      [{:keys [object-type selection-set variable-values visited-fragments]
        ;; 1. If visitedFragments if not provided, initialize it to the empty
        ;; set.
        :or {visited-fragments {}}}]

      (reduce
       (fn [grouped-fields [selection-type m]]
         (case selection-type
           :field

           (let [response-key
                 ;; TODO: The response-key will be the alias, if it exists
                 (:name m)]
             (update grouped-fields response-key (fnil conj []) m))

           :fragment-spread
           (throw (ex-info "TODO: fragment-spread" {}))

           :inline-fragment
           (throw (ex-info "TODO: inline-fragment" {}))))

       (ordered-map)                    ; grouped-fields
       selection-set))

    (defn ^{:crux.graphql.spec-ref/version "June2018"
            :crux.graphql.spec-ref/section "6.4.1"
            :crux.graphql.spec-ref/algorithm "CoerceArgumentValues"}
      coerce-argument-values
      [{:keys [object-type field variable-values]}]
      (throw (ex-info "TODO" (meta #'coerce-argument-values))))

    (defn ^{:crux.graphql.spec-ref/version "June2018"
            :crux.graphql.spec-ref/section "6.4.2"
            :crux.graphql.spec-ref/algorithm "ResolveFieldValue"}
      resolve-field-value
      [{:keys [object-type object-value field-name argument-values]}]
      (throw (ex-info "TODO" (meta #'resolve-field-value))))

    (defn ^{:crux.graphql.spec-ref/version "June2018"
            :crux.graphql.spec-ref/section "6.4.3"
            :crux.graphql.spec-ref/algorithm "CompleteValue"}
      complete-value
      [{:keys [field-type fields result variable-values]}]
      (throw (ex-info "TODO" (meta #'complete-value))))

    (defn
      ^{:crux.graphql.spec-ref/version "June2018"
        :crux.graphql.spec-ref/section "6.4"
        :crux.graphql.spec-ref/algorithm "ExecuteField"}
      execute-field
      [{:keys [object-type object-value field-type fields variable-values]}]

      ;; 1. Let field be the first entry in fields.
      (let [field (first fields)
            ;; 2. Let fieldName be the field name of field.
            field-name (:name field)
            ;; 3. Let argumentValues be the result of CoerceArgumentValues(…).
            argument-values
            (coerce-argument-values
             {:object-type object-type
              :field field
              :variable-values variable-values})

            ;; 4. Let resolvedValue be ResolveFieldValue(…).
            resolved-value
            (resolve-field-value
             {:object-type object-type
              :object-value object-value
              :field-name field-name
              :argument-values argument-values})]

        ;; 5. Return the result of CompleteValue(…).
        (complete-value {:field-type field-type
                         :fields fields
                         :resolved-value resolved-value
                         :variable-values variable-values})))

    (defn
      ^{:crux.graphql.spec-ref/version "June2018"
        :crux.graphql.spec-ref/section "6.3"
        :crux.graphql.spec-ref/algorithm "ExecuteSelectionSet"}
      execute-selection-set-normally
      "Return a map with :data and :errors."
      [{:keys [selection-set object-type object-value variable-values]}]
      ;; 1. Let groupedFieldSet be the result of CollectFields
      (let [grouped-field-set
            (collect-fields
             {:object-type object-type
              :selection-set selection-set
              :variable-values variable-values})
            ;; 2. Initialize resultMap to an empty ordered map.
            result-map (ordered-map)]

        ;; 3. For each groupedFieldSet as responseKey and fields:
        (reduce
         (fn [result-map [response-key fields]]
           ;; a. Let fieldName be the name of the first entry in fields. Note:
           ;; This value is unaffected if an alias is used.
           (let [field-name (:name (first fields))
                 ;; b. Let fieldType be the return type defined for the field fieldName of objectType.
                 field-type (some #(when (= (get % "name") field-name) (get % "type")) (get object-type "fields"))]

             ;; c. If fieldType is defined:
             (if field-type
               ;; i. Let responseValue be ExecuteField(objectType, objectValue,
               ;; fields, fieldType, variableValues).
               (let [response-value
                     (execute-field
                      {:object-type object-type
                       :object-value object-value
                       :field-type field-type
                       :fields fields
                       :variable-values variable-values})]
                 ;; ii. Set responseValue as the value for responseKey in resultMap.
                 (conj result-map [response-key response-value]))
               ;; Otherwise return the accumulator
               result-map)))
         result-map
         grouped-field-set)))

    (defn
      ^{:crux.graphql.spec-ref/version "June2018"
        :crux.graphql.spec-ref/section "6.2.1"
        :crux.graphql.spec-ref/algorithm "ExecuteQuery"}
      execute-query [{:keys [query schema variable-values initial-value]}]

      ;; 1. Let queryType be the root Query type in schema.
      (let [query-type-name (get schema "queryType")
            query-type (some #(when (= (get % "name") query-type-name) %) (get schema "types"))]

        ;; 2. Assert: queryType is an Object type.
        (when-not (= (get query-type "kind") "OBJECT")
          (throw (ex-info
                  "Query type must be an OBJECT"
                  (into
                   {:query-type query-type
                    :crux.graphql.spec-ref/step 2}
                   (meta #'execute-query)))))

        ;; 3. Let selectionSet be the top level Selection Set in query.
        (let [selection-set (:selection-set query)]
          ;; 4. Let data be the result of running ExecuteSelectionSet
          ;; normally (allowing parallelization).
          ;; 5. Let errors be any field errors produced while executing the selection set.
          ;; 6. Return an unordered map containing data and errors.
          (execute-selection-set-normally
           {:selection-set selection-set
            :object-type query-type
            :initial-value initial-value
            :variable-values variable-values}))))

    (defn
      ^{:crux.graphql.spec-ref/version "June2018"
        :crux.graphql.spec-ref/section "6.1"
        :crux.graphql.spec-ref/algorithm "ExecuteRequest"}
      execute-request [{:keys [schema document operation-name variable-values initial-value]}]
      ;; 1. Let operation be the result of GetOperation(document, operationName).
      (let [operation (graphql/get-operation document operation-name)
            ;; 2. Let coercedVariableValues be the result of
            ;; CoerceVariableValues(schema, operation, variableValues). (TODO)
            coerced-variable-values variable-values]

        (case (:operation-type operation)
          ;; 3. If operation is a
          "query" ;; operation:
          ;;   a. Return ExecuteQuery(operation, schema, coercedVariableValues,
          ;;   initialValue).
          (execute-query
           {:query operation
            :schema schema
            :variable-values coerced-variable-values
            :initial-value initial-value})

          (throw (ex-info "No operation type on operation" {:operation operation}))

          ;; 4. Otherwise if operation is a mutation operation:
          ;;   a. Return ExecuteMutation(operation, schema, coercedVariableValues, initialValue).

          ;; 5. Otherwise if operation is a subscription operation:
          ;;   a. Return Subscribe(operation, schema, coercedVariableValues, initialValue).
          )))

    (defn- attribute-to-graphql-field [db attr]
      (let [{:crux.graphql/keys [name]
             :crux.schema/keys [description cardinality]
             t :crux.schema/type
             :as args} attr]
        ;; Name is mandatory, ex-info is missing!
        (cond-> {}
          true (conj
                (if name
                  ["name" name]
                  (throw (ex-info "Missing :crux.graphql/name" {:attribute attr}))))
          description (conj ["description" description])
          ;; TODO: args
          t (conj {"type"
                   (cond
                     (= t String)
                     {"kind" "SCALAR" "name" "String"}
                     (= t Long)
                     {"kind" "SCALAR" "name" "Long"}
                     (keyword? t)
                     (let [{:crux.graphql/keys [name]} (crux/entity db t)]
                       (case cardinality
                         :crux.schema.cardinality/zero-or-more
                         {"kind" "LIST"
                          "name" nil
                          "ofType" name}

                         {"kind" "OBJECT"
                          "name" name
                          "ofType" nil})))})
          true (conj {"isDeprecated" false}))))

    (defn- entity-to-graphql-type [db e]
      (cond
        (= e String)
        {"kind" "SCALAR" "name" "String"}
        (= e Long)
        {"kind" "SCALAR" "name" "Long"}
        (map? e)
        (let [{:crux.graphql/keys [name]
               :crux.schema/keys [description attributes]
               t :crux.schema/type} e]
          (cond-> {}
            t (conj ["kind" "OBJECT"])
            name (conj ["name" name])
            description (conj ["description" description])
            attributes (conj ["fields" (mapv #(attribute-to-graphql-field db %) (vals attributes))])))
        :else (throw (ex-info "Condition not supported" {:entity e}))))

    (with-open
      [node (crux/start-node {:crux.node/topology '[crux.standalone/topology]})]
      (crux/await-tx
       node
       (crux/submit-tx
        node
        (for [ent
              [{
                ;; The Crux id also acts as the name of the relation schema
                ;; represented by this entity.
                :crux.db/id :ex.type/film
                :crux.schema/type :crux.schema.type/relation
                :crux.schema/description "A James Bond Film"
                :crux.graphql/name "Film"
                :crux.schema/attributes
                {:film/name
                 {:crux.graphql/name "filmName"
                  :crux.schema/required? true}
                 :film/box
                 {:crux.schema/type Long
                  :crux.schema/description "How much the film made at the box office"
                  :crux.schema/required? true
                  :crux.graphql/name "box"}
                 :film/cost
                 {:crux.schema/description "How much the film cost to make"
                  :crux.schema/type Long
                  :crux.schema/required? true
                  :crux.graphql/name "cost"}
                 :film/earnings
                 {:crux.schema/type String
                  :crux.schema/derived
                  ^:crux.memoize '(fn [{:film/keys [box cost]}] (- box cost))
                  :crux.graphql/name "earnings"}
                 :film/vehicle
                 {:crux.schema/type :ex.type/vehicle
                  :crux.schema/cardinality :crux.schema.cardinality/zero-or-more
                  :crux.graphql/name "vehicles"}}}

               {:crux.db/id :ex.type/vehicle
                :crux.schema/type :crux.schema.type/relation
                :crux.graphql/name "Vehicle"
                :crux.schema/attributes
                {:vehicle/brand
                 {:crux.schema/type String
                  :crux.graphql/name "brand"}
                 :vehicle/model
                 {:crux.schema/type String
                  :crux.graphql/name "model"}}}

               {:crux.db/id :ex.type/graphql-query-root
                :crux.schema/type :crux.schema.type/relation
                :crux.graphql/name "Root"
                :crux.schema/attributes
                {:all-films
                 {:crux.schema/description "All the films in the James Bond universe."
                  :crux.schema/type :ex.type/film
                  :crux.schema/cardinality :crux.schema.cardinality/zero-or-more
                  :crux.graphql/name "allFilms"}
                 :film
                 {:crux.schema/description "A particular film in the James Bond universe."
                  :crux.schema/type :ex.type/film
                  :crux.graphql/name "film"}}}
               ]]
          [:crux.tx/put ent])))

      (let [db (crux/db node)
            schema
            (-> {"queryType" "Root"
                 "types"
                 (mapv #(entity-to-graphql-type db %)
                       [(crux/entity db :ex.type/graphql-query-root)
                        ;; TODO: All the rest should be discovered via graph traversal
                        (crux/entity db :ex.type/film)
                        (crux/entity db :ex.type/vehicle)
                        String
                        ])
                 "directives" []}
                validate-schema)]

        ;; Extract GraphQL types to file, just to compare
        (spit "/tmp/schema.json"
              (json/generate-string
               {:data
                {"__schema" schema}}
               {:pretty true}))

        ;; Query the schema with GraphQL execution

        (let [document (->
                        "query GetFilms { allFilms { filmName }}"
                        #_introspection-query
                        graphql/parse-graphql
                        graphql/validate-graphql-document)]

          ;; TODO: Introspection (Attempt to execute this query against the schema)

          #_schema
          ;;
          (execute-request
           {:schema schema
            :document document
            :operation-name "GetFilms" #_"IntrospectionQuery"
            :variable-values {}
            :initial-value (crux/entity db :ex.type/graphql-query-root)}))))))

;; https://en.wikipedia.org/wiki/Functional_dependency
;; https://en.wikipedia.org/wiki/Armstrong%27s_axioms
;; https://en.wikipedia.org/wiki/The_Third_Manifesto
