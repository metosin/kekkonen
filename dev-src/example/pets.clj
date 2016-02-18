(ns example.pets
  (:require [org.httpkit.server :as server]
            [kekkonen.cqrs :refer :all]
            [plumbing.core :refer [defnk fnk]]
            [schema.core :as s]
            [kekkonen.core :as k]))

;;
;; Schemas
;;

(s/defschema Pet
  {:id s/Int
   :name s/Str
   :species s/Str
   :size (s/enum :S :M :L)})

(s/defschema NewPet
  (dissoc Pet :id))

;;
;; Commands & Queries
;;

(defnk ^:query get-pets
  "Retrieves all pets"
  {:responses {:default {:schema [Pet]}}}
  [[:system db]]
  (success (vals @db)))

(defnk ^:command add-pet
  "Adds an pet to database"
  {:responses {:default {:schema Pet}}}
  [[:system db ids]
   data :- NewPet]

  (if (-> data :size (= :L))
    (failure "Can't have large pets")
    (let [item (assoc data :id (swap! ids inc))]
      (swap! db assoc (:id item) item)
      (success item))))


(defn- forward [dispatcher action ctx data]
  (try
    (k/invoke dispatcher action (assoc ctx :data data))
    (catch Exception e
      (ex-data e))))

(defnk ^:command speculative
  "Dummy implementation. In real life, use a real TX system such as the RDB"
  {:summary "Runs a speculative transaction."}
  [[:system db ids]
   [:data action :- s/Keyword, data :- s/Any]
   :as ctx]

  (let [db' (atom @db)
        ids' (atom @ids)
        ctx' (assoc ctx :system {:db db', :ids ids'})

        dispatcher (k/get-dispatcher ctx)
        response (forward dispatcher action ctx' data)]

    response))

(defnk ^:command transact
  "Dummy implementation. In real life, use a real TX system such as the RDB"
  {:summary "Runs multiple commands in a single transaction."}
  [[:system db ids]
   [:data commands :- [{:action s/Keyword
                        :data s/Any}]] :as ctx]

  (let [db' (atom @db)
        ids' (atom @ids)
        ctx' (assoc ctx :system {:db db', :ids ids'})

        dispatcher (k/get-dispatcher ctx)
        responses (map
                    (fnk [action data]
                      (forward dispatcher action ctx' data))
                    commands)

        {successed true, failed false} (group-by success? responses)
        should-commit? (not (seq failed))
        response (if should-commit? success failure)]

    (when should-commit?
      (reset! db @db')
      (reset! ids @ids'))

    (response {:success successed
               :failed failed})))

;;
;; Application
;;

(def app
  (cqrs-api
    {:core {:handlers {:pets [#'get-pets #'add-pet]
                       :tx [#'transact #'speculative]}
            :context {:system {:db (atom {})
                               :ids (atom 0)
                               :counter (atom 0)}}}}))

(comment
  (server/run-server #'app {:port 4000}))

(comment
  {:commands
   [{:action :pets/add-pet
     :data {:name "Ruusu", :species "Cow", :size :L}}
    {:action :pets/add-pet
     :data {:name "Anselmi", :species "SpaceMonkey" :size :M}}]})
