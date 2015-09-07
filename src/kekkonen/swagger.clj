(ns kekkonen.swagger
  (:require [schema.core :as s]
            [ring.swagger.swagger2 :as rs2]
            [kekkonen.core :as k]
            [kekkonen.common :as kc]
            [plumbing.core :as p]
            [kekkonen.ring :as r]))

(defn transform-handler
  "Transforms a handler into ring-swagger path->method->operation map."
  [handler]
  (let [{:keys [description input ns type] {:keys [summary responses]} :user} handler
        {:keys [body-params query-params path-params header-params]} (:request input)
        methods (some-> r/+default-options+ :types type :methods sort)
        path (r/handler-uri handler)]
    {path (p/for-map [method methods]
            method (merge
                     {:tags [ns]}
                     (if summary {:summary summary})
                     (if responses {:responses responses})
                     {:parameters (kc/strip-nil-values
                                    {:body body-params
                                     :query query-params
                                     :path path-params
                                     :header header-params})}
                     (if description {:description description})))}))

(s/defn swagger-object
  "Creates a Swagger-spec object out of ring-swagger object and ring-swagger options."
  ([swagger :- rs2/Swagger]
    (swagger-object swagger {}))
  ([swagger :- rs2/Swagger, options :- k/KeywordMap]
    (rs2/swagger-json swagger options)))

(s/defn swagger :- rs2/Swagger
  "Creates a ring-swagger object out of Kekkonen and extra info"
  ([kekkonen]
    (swagger kekkonen {}))
  ([kekkonen info]
    (let [handlers (k/all-handlers kekkonen)]
      (merge
        info
        {:paths (apply merge (map transform-handler handlers))}))))
