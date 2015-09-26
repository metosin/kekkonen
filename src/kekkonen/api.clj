(ns kekkonen.api
  (:require [kekkonen.ring :as r]
            [kekkonen.core :as k]
            [kekkonen.middleware :as mw]
            [ring.util.http-response :refer [ok]]
            [kekkonen.swagger :as ks]
            [schema.core :as s]
            [kekkonen.common :as kc]
            [plumbing.core :as p]))

;; TODO: add check
(defn kekkonen-handlers [type]
  {:kekkonen
   [(k/handler
      {:type type
       :name "get-all"
       :description "Returns a list of all handlers in a given namespace."}
      (p/fnk [[:data {ns :- s/Keyword nil}] :as context]
        (ok (->> context
                 k/get-dispatcher
                 (p/<- (k/all-handlers ns))
                 (filter (p/fn-> :ring))
                 (remove (p/fn-> :ns (= :kekkonen)))
                 (remove (p/fn-> :user :no-doc))
                 (map k/public-handler)))))
    (k/handler
      {:type type
       :name "get-available"
       :description "Returns a list of all available handlers in a given namespace."}
      (p/fnk [[:data {ns :- s/Keyword nil}] :as context]
        (ok (->> context
                 k/get-dispatcher
                 (p/<- (k/available-handlers context ns))
                 (filter (p/fn-> :ring))
                 (remove (p/fn-> :ns (= :kekkonen)))
                 (remove (p/fn-> :user :no-doc))
                 (map k/public-handler)))))
    (k/handler
      {:type type
       :name "get-handler"
       :description "Returns a handler info or nil."}
      (p/fnk [[:data action :- s/Keyword] :as context]
        (ok (k/public-handler
              (k/some-handler
                (k/get-dispatcher context)
                action)))))]})

(s/defschema Options
  {:core k/KeywordMap
   (s/optional-key :api) k/KeywordMap
   (s/optional-key :ring) k/KeywordMap
   (s/optional-key :mw) k/KeywordMap
   (s/optional-key :info) k/KeywordMap
   (s/optional-key :swagger) k/KeywordMap
   (s/optional-key :swagger-ui) k/KeywordMap})

(s/def +default-options+ :- Options
  {:core (assoc k/+default-options+ :coercion {:input nil, :output nil})
   :api {:handlers (kekkonen-handlers :handler)}
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
      (mw/api-middleware
        (r/routes
          [(r/ring-handler dispatcher (:ring options))
           (ks/swagger-ui (:swagger-ui options))])
        (:mw options)))))
