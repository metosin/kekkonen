(ns example.cqrs
  (:require [ring.adapter.jetty :as jetty]
            [kekkonen.cqrs :refer :all]
            [plumbing.core :as p]
            [schema.core :as s]))

(p/defnk ^:query get-items :- #{s/Str}
  "Retrieves all"
  [[:components db]]
  (success @db))

(p/defnk ^:command add-item! :- #{s/Str}
  "Adds an item to database"
  [[:data item :- String]
   [:components db]]
  (success (swap! db conj item)))

(p/defnk ^:command reset-items! :- #{s/Str}
  "Resets the database"
  [[:components db]]
  (success (swap! db empty)))

(def app
  (cqrs-api
    {:core {:handlers {:api 'example.cqrs}
            :context {:components {:db (atom #{})}}}}))

(defn start []
  (def server
    (jetty/run-jetty
      #'app
      {:port 3000
       :join? false})))

(defn stop []
  (.stop server))

(comment
  (start)
  (stop))
