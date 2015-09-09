(ns example.cqrs
  (:require [ring.adapter.jetty :as jetty]
            [kekkonen.cqrs :refer :all]
            [plumbing.core :as p]
            [schema.core :as s]
            [clojure.set :as set]))

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
  {:roles #{:boss}}
  [[:components db]]
  (success (swap! db empty)))

(p/defnk ^:query ping [] (success {:ping "pong"}))
(p/defnk ^:query pong [] (success {:pong "ping"}))

(p/defnk ^:query plus
  {:responses {success-status {:schema {:result s/Int}}}}
  [[:data x :- s/Int, y :- s/Int]]
  (success {:result (+ x y)}))

(p/defnk ^:command times
  {:responses {success-status {:schema {:result s/Int}}}}
  [[:data x :- s/Int, y :- s/Int]]
  (success {:result (* x y)}))

(p/defnk ^:query get-user
  {:responses {success-status {:schema (s/maybe security/User)}}}
  [user]
  (success user))

;;
;; Security
;;

(s/defschema User
  {:name s/Str
   :roles #{s/Keyword}})

(defn api-key-authenticator [context]
  (let [api-key (-> context :request :query-params :api_key)
        user (condp = api-key
               "123" {:name "Seppo" :roles #{}}
               "234" {:name "Sirpa" :roles #{:boss}}
               nil)]
    (assoc context :user user)))

(defn require-roles [context required]
  (let [roles (-> context :user :roles)]
    (if (seq (set/intersection roles required))
      context
      (error! {:code "Missing role"
               :roles roles
               :required required}))))

(require-roles {:user {:roles #{:boss}}} #{:boss})

;;
;; Application
;;

(def app
  (cqrs-api
    {:info {:info {:title "Kekkonen"}}
     :core {:handlers {:api {:item [#'get-items #'add-item! #'reset-items!]
                             :calculator [#'plus #'times]
                             :system [#'ping #'pong #'get-user]}}
            :context {:components {:db (atom {})
                                   :ids (atom 0)}}
            :user {:roles require-roles}}
     :ring {:transformers [api-key-authenticator]}}))

(defn start []
  (jetty/run-jetty
    #'app
    {:port 3000
     :join? false}))

(comment
  (start))
