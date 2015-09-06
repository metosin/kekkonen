(ns kekkonen.cqrs
  (:require [kekkonen.core :as k]
            [ring.util.http-response :as hr]))

(def cqrs-types {:query {:methods #{:get}
                         :transformers [(k/context-copy [:request :query-params] [:data])]}
                 :command {:methods #{:post}
                           :transformers [(k/context-copy [:request :body-params] [:data])]}})

(def cqrs-type-resolver (k/type-resolver :command :query))

(def success hr/ok)
(def failure hr/bad-request)
(def error hr/internal-server-error)
