(ns kekkonen.cqrs
  (:require [kekkonen.core :as k]
            [kekkonen.api :as ka]
            [kekkonen.common :as kc]
            [ring.util.http-response :as hr]
            [ring.util.http-status :as hs]
            [ring.util.http-predicates :as hp]))

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

;;
;; api
;;

(defn cqrs-api [options]
  (ka/api
    (kc/deep-merge
      {:core {:type-resolver (k/type-resolver :command :query)
              :handlers {:kekkonen [(k/handler
                                      {:type :query
                                       :name "all"}
                                      (fn [context]
                                        (success
                                          (->> context
                                               k/get-kekkonen
                                               k/all-handlers
                                               (map k/->public)))))]}}
       :ring {:types {:query {:methods #{:get}
                              :parameters [[[:request :query-params] [:data]]]}
                      :command {:methods #{:post}
                                :parameters [[[:request :body-params] [:data]]]}}}}
      options)))

