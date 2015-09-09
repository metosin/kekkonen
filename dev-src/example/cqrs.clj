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
  {:id s/Int
   :name s/Str
   :size (s/enum :S :M :L)
   (s/optional-key :description) s/Str
   :origin {:country (s/enum :FI :PO)}})

(s/defschema AddNewItem
  (dissoc Item :id))

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
   data :- AddNewItem]
  (let [id (swap! ids inc)
        item (assoc data :id id)]
    (success
      (get (swap! db assoc id item) id))))

(p/defnk ^:command reset-items!
  "Resets the database"
  [[:components db]]
  (success (swap! db empty)))

(p/defnk ^:query ping [] (success {:ping "pong"}))
(p/defnk ^:query pong [] (success {:pong "ping"}))

(p/defnk ^:query plus
  [[:data x :- s/Int, y :- s/Int]]
  (success {:resutl (+ x y)}))

;;
;; Application
;;

(def app
  (cqrs-api
    {:info {:info {:title "Kekkonen"}}
     :core {:handlers {:api {:item [#'get-items #'add-item! #'reset-items!]
                             :calculator [#'plus]
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
