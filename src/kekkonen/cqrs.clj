(ns kekkonen.cqrs
  (:require [kekkonen.core :as k]
            [kekkonen.api :as ka]
            [kekkonen.common :as kc]
            [ring.util.http-response :as hr]
            [ring.util.http-status :as hs]
            [ring.util.http-predicates :as hp]
            [schema.core :as s]
            [kekkonen.ring :as r]))

;;
;; response wrappers
;;

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

;;
;; Actions
;;

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
      {:core {:type-resolver (k/type-resolver :command :query)}
       :api {:handlers (r/kekkonen-handlers :query :command)}
       :ring {:types {:query {:methods #{:get}
                              :parameters {[:data] [:request :query-params]}}
                      :command {:methods #{:post}
                                :parameters {[:data] [:request :body-params]}}}}}
      options)))
