(ns sample.handler
  (:require [org.httpkit.server :as server]
            [plumbing.core :refer [defnk]]
            [kekkonen.cqrs :refer :all]))

(defnk ^:query hello-world
  "says hallo"
  {:responses {:default {:schema {:message String}}}}
  [[:data name :- String]]
  (success {:message (str "Hello, " name)}))

(def app (cqrs-api {:core {:handlers {:api #'hello-world}}}))

(defn start []
  (server/run-server #'app {:port 3000})
  (println "server running in port 3000"))

; ... or as a one-liner with vanilla Clojure
;
; (defn start []
;   (server/run-server
;    (cqrs-api
;      {:core 
;        {:handlers 
;          {:api 
;            (query
;              {:name 'hello-world
;               :input {:data {:name String}}
;               :responses {:default {:schema {:result String}}}}
;               (fn [context]
;                 (success {:response (str "Hello, " (-> context :data :name))})))}}})
;    {:port 3000}))