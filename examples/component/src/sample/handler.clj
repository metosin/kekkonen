(ns sample.handler
  (:require [org.httpkit.server :as server]
            [plumbing.core :refer [defnk]]
            [kekkonen.cqrs :refer :all]
            [schema.core :as s]))

(s/defschema Pizza
  {:name s/Str
   (s/optional-key :description) s/Str
   :size (s/enum :S :M :L)
   :origin {:country (s/enum :FI :PO)}})

;;
;; Handlers
;;

(defnk ^:query ping [] (success {:ping "pong"}))

(defnk ^:command echo-pizza
  "Echoes a pizza"
  {:responses {:default {:schema Pizza}}}
  [data :- Pizza]
  (success data))

(defnk ^:query plus
  "playing with data"
  [[:data x :- s/Int, y :- s/Int]]
  (success {:result (+ x y)}))

(defnk ^:command inc!
  "a stateful counter"
  [[:components counter]]
  (success {:result (swap! counter inc)}))

;;
;; Application
;;

(defn new-app [system]
  (cqrs-api
    {:info {:info {:title "Kekkonen with Component"
                   :description "created with http://kekkonen.io"}}
     :core {:handlers {:api {:pizza #'echo-pizza
                             :example [#'ping #'inc! #'plus]}}
            :context {:components system}}}))
