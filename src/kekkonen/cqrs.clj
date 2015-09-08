(ns kekkonen.cqrs
  (:require [kekkonen.core :as k]
            [kekkonen.api :as ka]
            [kekkonen.common :as kc]
            [ring.util.http-response :as hr]))

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

(def success hr/ok)
(def failure hr/bad-request)
(def error hr/internal-server-error)
