(ns kekkonen.api
  (:require [kekkonen.ring :as r]
            [kekkonen.core :as k]
            [kekkonen.middleware :as mw]
            [kekkonen.swagger :as ks]
            [schema.core :as s]
            [kekkonen.common :as kc]))

(s/defschema Options
  {:core k/KeywordMap
   (s/optional-key :api) {:handlers k/KeywordMap}
   (s/optional-key :ring) r/Options
   (s/optional-key :mw) k/KeywordMap
   (s/optional-key :info) k/KeywordMap
   (s/optional-key :swagger) k/KeywordMap
   (s/optional-key :swagger-ui) k/KeywordMap})

(s/def +default-options+ :- Options
  {:core (assoc k/+default-options+ :coercion {:input nil, :output nil})
   :api {:handlers (r/kekkonen-handlers :handler :handler)}
   :ring r/+default-options+
   :mw mw/+default-options+
   :info {}
   :swagger {}
   :swagger-ui ks/+default-swagger-ui-options+})

(s/defn api [options :- Options]
  (s/with-fn-validation
    (let [options (kc/deep-merge +default-options+ options)
          info (merge (:info options) (mw/api-info (:mw options)))
          dispatcher (-> (k/dispatcher (:core options))
                         (k/inject (-> options :api :handlers))
                         (k/inject (ks/swagger-handler info options)))]
      (mw/wrap-api
        (r/routes
          [(r/ring-handler dispatcher (:ring options))
           (ks/swagger-ui (:swagger-ui options))])
        (:mw options)))))
