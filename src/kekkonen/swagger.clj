(ns kekkonen.swagger
  (:require [schema.core :as s]
            [ring.swagger.swagger2 :as rs2]
            [ring.util.http-response :refer [ok]]
            [ring.swagger.ui :as ui]
            [kekkonen.core :as k]
            [kekkonen.common :as kc]
            [plumbing.core :as p]
            [kekkonen.ring :as r]))

(def +default-swagger-ui-options+
  {:path "/"})

(defn transform-handler
  "Transforms a handler into ring-swagger path->method->operation map."
  [handler]
  (let [{:keys [description ns ring] {:keys [summary responses no-doc]} :user} handler
        {:keys [parameters input type-config]} ring
        ;; deep-merge back the mappings to get right request requirements
        input (reduce kc/deep-merge-to-from input parameters)
        {:keys [body-params query-params path-params header-params]} (:request input)
        methods (-> type-config :methods sort)
        path (r/handler-uri handler)]

    ;; discard handlers with :no-doc or without :ring metadata
    (if (and (not no-doc) ring)
      {path (p/for-map [method methods]
              method (merge
                       (if ns {:tags [ns]})
                       (if description {:description description
                                        :summary description})
                       (if summary {:summary summary})
                       (if responses {:responses responses})
                       {:parameters (kc/strip-nil-values
                                      {:body body-params
                                       :query query-params
                                       :path path-params
                                       :header header-params})}))})))

(s/defn ring-swagger :- rs2/Swagger
  "Creates a ring-swagger object out of Registry and extra info"
  [registry info]
  (let [handlers (k/all-handlers registry)]
    (merge
      info
      {:paths (apply merge (map transform-handler handlers))})))

(s/defn swagger-object
  "Creates a Swagger-spec object out of ring-swagger object and ring-swagger options."
  [ring-swagger :- rs2/Swagger, options :- k/KeywordMap]
  (rs2/swagger-json ring-swagger options))

(s/defn swagger-ui
  "Ring handler for the Swagger UI"
  [options]
  (apply ui/swagger-ui (into [(:path options)] (apply concat (dissoc options :path)))))

(defn swagger-handler [info options]
  (k/handler
    {:type :kekkonen.ring/handler
     :name "swagger.json"
     :no-doc true}
    (fn [context]
      (let [registry (k/get-registry context)]
        (ok (swagger-object
              (ring-swagger registry info)
              options))))))
