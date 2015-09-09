(ns kekkonen.cqrs
  (:require [kekkonen.core :as k]
            [kekkonen.api :as ka]
            [kekkonen.common :as kc]
            [ring.util.http-response :as hr]
            [ring.util.http-status :as hs]
            [ring.util.http-predicates :as hp]
            [kekkonen.ring :as r]))

(def +cqrs-types+ {:query {:methods #{:get}
                           :parameters [[[:request :query-params] [:data]]]}
                   :command {:methods #{:post}
                             :parameters [[[:request :body-params] [:data]]]}})

(def +cqrs-type-resolver+ (k/type-resolver :command :query))

(defn cqrs-api [options]
  (ka/api
    (kc/deep-merge
      {:core {:type-resolver +cqrs-type-resolver+}
       :ring {:types +cqrs-types+}}
      options)))

;;
;; Return types
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

