(ns kekkonen.ring
  (:require [schema.core :as s]
            [schema.coerce :as sc]
            [schema.utils :as su]
            [ring.swagger.coerce :as rsc]
            [kekkonen.core :as k]
            [kekkonen.common :as kc]
            [clojure.string :as str]))

(s/defschema Options
  {:types {s/Keyword {:methods #{s/Keyword}
                      (s/optional-key :parameters) [[(s/one [s/Keyword] 'from) 
                                                     (s/one [s/Keyword] 'to)]]
                      (s/optional-key :transformers) [k/Function]}}
   :coercion {s/Keyword k/Function}})

(s/def +default-options+ :- Options
  {:types {:handler {:methods #{:post}
                     :parameters [[[:request :body-params] [:data]]]
                     :transformers [(k/context-copy [:request :body-params] [:data])]}}
   :coercion {:query-params rsc/query-schema-coercion-matcher
              :path-params rsc/query-schema-coercion-matcher
              :form-params rsc/query-schema-coercion-matcher
              :header-params rsc/query-schema-coercion-matcher
              :body-params rsc/json-schema-coercion-matcher}})

(s/defn uri->action :- s/Keyword
  "Converts an action keyword from a uri string."
  [path :- s/Str]
  (-> path (subs 1) keyword))

(s/defn handler-uri :- s/Str
  "Creates a uri for the handler"
  [{:keys [ns name]} :- k/Handler]
  (str/replace (str ns name) #"[:|\.]" "/"))

(defn coerce! [schema matcher value in type]
  (let [coercer (sc/coercer schema matcher)
        coerced (coercer value)]
    (if-not (su/error? coerced)
      coerced
      (throw
        (ex-info
          "Coercion error"
          {:type type
           :in in
           :value value
           :schema schema
           :error coerced})))))

(defn coerce-request!
  "Coerces a request against a handler input schema based on :coercion options."
  [request handler {:keys [coercion]}]
  (reduce
    (fn [request [k matcher]]
      (if-let [schema (get-in handler [:input :request k])]
        (let [value (get request k {})
              coerced (coerce! schema matcher value k ::request)]
          (assoc request k coerced))
        request))
    request
    coercion))

(s/defn ring-handler
  "Creates a ring handler from Kekkonen and options."
  ([kekkonen]
    (ring-handler kekkonen {}))
  ([kekkonen, options :- k/KeywordMap]
    (let [options (kc/deep-merge +default-options+ options)]
      (fn [{:keys [request-method uri] :as request}]
        (let [action (uri->action uri)]
          (if-let [handler (k/some-handler kekkonen action)]
            (if-let [{:keys [methods transformers]} (get (:types options) (:type handler))]
              (if (get methods request-method)
                (let [request (coerce-request! request handler options)
                      context {:request request}
                      context (reduce (fn [context mapper] (mapper context)) context transformers)
                      responses (-> handler :user :responses)
                      response (k/invoke kekkonen action context)]
                  (if responses
                    (let [status (or (:status response) 200)
                          schema (get-in responses [status :schema])
                          matcher (get-in options [:coercion :body-params])
                          value (:body response)]
                      (if schema
                        (let [coerced (coerce! schema matcher value :response ::response)]
                          (assoc response :body coerced))
                        response))
                    response))))))))))

(s/defn routes :- k/Function
  "Creates a ring handler of multiples handlers, matches in orcer."
  [ring-handlers :- [k/Function]]
  (apply some-fn ring-handlers))

(s/defn match
  ([match-uri ring-handler]
    (match match-uri identity ring-handler))
  ([match-uri match-request-method ring-handler]
    (fn [{:keys [uri request-method] :as request}]
      (if (and (= match-uri uri)
               (match-request-method request-method))
        (ring-handler request)))))
