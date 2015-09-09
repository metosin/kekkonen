(ns example.cqrs
  (:require [ring.adapter.jetty :as jetty]
            [kekkonen.cqrs :refer :all]
            [plumbing.core :as p]
            [schema.core :as s]))

;;
;; Schemas
;;

(s/defschema Item
  "A database item"
  {:name s/Str
   :size (s/enum :S :M :L)
   (s/optional-key :description) s/Str
   :origin {:country (s/enum :FI :PO)}})

;;
;; Commands & Queries
;;

(p/defnk ^:query get-items
  "Retrieves all items"
  {:responses {success-status {:schema [Item]}}}
  [[:components db]]
  (success (vals @db)))

(p/defnk ^:command add-item!
  "Adds an item to database"
  {:responses {success-status {:schema Item}}}
  [[:components db ids]
   data :- Item]
  (let [id (swap! ids (comp keyword inc))]
    (success
      (get (swap! db assoc id data) id))))

(p/defnk ^:command reset-items!
  "Resets the database"
  [[:components db]]
  (success (swap! db empty)))

(p/defnk ^:query ping [] (success {:ping "pong"}))
(p/defnk ^:query pong [] (success {:pong "ping"}))

;;
;; Application
;;

(def app
  (cqrs-api
    {:info {:info {:title "Kekkonen"}}
     :core {:handlers {:api {:item [#'get-items #'add-item! #'reset-items!]
                             :system [#'ping #'pong]}}
            :context {:components {:db (atom {})
                                   :ids (atom 0)}}}}))

(defn start []
  (jetty/run-jetty
    #'app
    {:port 3000
     :join? false}))

(comment
  (start))
