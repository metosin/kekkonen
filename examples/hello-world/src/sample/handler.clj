(ns sample.handler
  (:require [org.httpkit.server :as server]
            [plumbing.core :refer [defnk]]
            [kekkonen.cqrs :refer :all]))

(defnk ^:query hello [[:data name :- String]]
  (success {:message (str "Hello, " name)}))

(def app (cqrs-api {:core {:handlers {:api #'hello}}}))

(defn start []
  (server/run-server #'app {:port 3000}))

; ... or as a one-liner with vanilla Clojure
;
; (defn start []
;   (server/run-server
;    (cqrs-api
;      {:core
;        {:handlers
;          {:api
;            (query
;              {:name "hello"
;               :handle (fn [{{:keys [name]} :data}]
;                         (success {:message (str "Hello, " name)}))})}}})
;    {:port 3000}))
