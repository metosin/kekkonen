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
   (s/optional-key :swagger) ks/Options})

(s/def +default-options+ :- Options
  {:core (-> k/+default-options+
             (assoc :coercion {:input nil, :output nil})
             (update :meta kc/merge-map-like r/+ring-meta+))
   :api {:handlers r/+kekkonen-handlers+}
   :ring r/+default-options+
   :mw mw/+default-options+
   :swagger {:data {:info {:title "Kekkonen API"
                           :version "0.0.1"}}}})

(defn api [options]
  (s/with-fn-validation
    (let [options (s/validate Options (kc/deep-merge-map-like +default-options+ options))
          api-handlers (-> options :api :handlers)
          swagger-data (merge (-> options :swagger :data) (mw/api-info (:mw options)))
          swagger-options (-> options :swagger)
          swagger-handler (ks/swagger-handler swagger-data swagger-options)
          dispatcher (cond-> (k/dispatcher (:core options))
                             api-handlers (k/inject api-handlers)
                             swagger-handler (k/inject swagger-handler))]
      (mw/wrap-api
        (r/routes
          [(r/ring-handler dispatcher (:ring options))
           (ks/swagger-ui swagger-options)])
        (:mw options)))))
