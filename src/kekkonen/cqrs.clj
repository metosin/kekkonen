(ns kekkonen.cqrs
  (:require [kekkonen.core :as k]
            [kekkonen.api :as ka]
            [kekkonen.common :as kc]
            [ring.util.http-response :as hr]
            [ring.util.http-status :as hs]
            [ring.util.http-predicates :as hp]
            [schema.core :as s]
            [plumbing.core :as p]))

;;;
;;; statuses
;;;

(def success hr/ok)
(def failure hr/bad-request)
(def error hr/internal-server-error)

(def failure! hr/bad-request!)
(def error! hr/internal-server-error!)

(def success-status hs/ok)
(def failure-status hs/bad-request)
(def error-status hs/internal-server-error)

(def success? hp/ok?)
(def failure? hp/bad-request?)
(def error? hp/internal-server-error?)

(s/defn command
  [meta :- k/KeywordMap, f :- k/Function]
  (vary-meta f merge {:type :command} meta))

(s/defn query
  [meta :- k/KeywordMap, f :- k/Function]
  (vary-meta f merge {:type :query} meta))

;;
;; api
;;

(defn cqrs-api [options]
  (ka/api
    (kc/deep-merge
      {:core {:type-resolver (k/type-resolver :command :query)
              :handlers {:kekkonen [(k/handler
                                      {:type :query
                                       :name "get-all"
                                       :description "Returns a list of all handlers."}
                                      (fn [context]
                                        (success
                                          (->> context
                                               k/get-registry
                                               k/all-handlers
                                               (map k/public-meta)))))
                                    (k/handler
                                      {:type :query
                                       :name "get-available"
                                       :description "Returns a list of all available handlers."}
                                      (fn [context]
                                        (success
                                          (->> context
                                               k/get-registry
                                               ((partial k/available-handlers context))
                                               (map k/public-meta)))))
                                    (k/handler
                                      {:type :query
                                       :name "get-handler"
                                       :description "Returns a handler info or nil."}
                                      (p/fnk [[:data action :- s/Keyword] :as context]
                                        (success
                                          (k/public-meta
                                            (k/some-handler
                                              (k/get-registry context)
                                              action)))))
                                    (k/handler
                                      {:type :command
                                       :name "validate"
                                       :description "Validates an action against the given context."}
                                      (p/fnk [[:request [:query-params action :- s/Keyword]]
                                              data :- k/KeywordMap
                                              :as context]
                                        (success
                                          (k/validate
                                            (k/get-registry context)
                                            action
                                            context))))]}}
       :ring {:types {:query {:methods #{:get}
                              :parameters [[[:request :query-params] [:data]]]}
                      :command {:methods #{:post}
                                :parameters [[[:request :body-params] [:data]]]}}}}
      options)))

