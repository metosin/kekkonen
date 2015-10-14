(ns example.cqrs
  (:require [org.httpkit.server :as server]
            [kekkonen.cqrs :refer :all]
            [plumbing.core :as p]
            [schema.core :as s]
            [clojure.set :as set]))

;;
;; Security
;;

(s/defschema User
  {:name s/Str
   :roles #{s/Keyword}})

(defn api-key-authenticator [context]
  (let [api-key (-> context :request :query-params :api_key)
        user (condp = api-key
               "seppo" {:name "Seppo" :roles #{}}
               "sirpa" {:name "Sirpa" :roles #{:boss}}
               nil)]
    (assoc context :user user)))

(defn require-roles [required]
  (fn [context]
    (let [roles (-> context :user :roles)]
      (if (seq (set/intersection roles required))
        context))))

(p/defnk ^:query get-user
  {:responses {success-status {:schema (s/maybe User)}}}
  [user] (success user))

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
  {::roles #{:boss}}
  [[:components db]
   [:data really :- s/Bool]]
  (success
    (if really
      (swap! db empty)
      @db)))

(p/defnk ^:query ping [] (success {:ping "pong"}))
(p/defnk ^:query pong [] (success {:pong "ping"}))

;;
;; parameters
;;

(p/defnk ^:query plus
  {:responses {success-status {:schema {:result s/Int}}}}
  [[:data x :- s/Int, y :- s/Int]]
  (success {:result (+ x y)}))

(p/defnk ^:query times
  {:responses {success-status {:schema {:result s/Int}}}}
  [[:data x :- s/Int, y :- s/Int]]
  (success {:result (* x y)}))

(p/defnk ^:command inc!
  {:responses {success-status {:schema {:result s/Int}}}}
  [[:components counter]]
  (success {:result (swap! counter inc)}))

;;
;; Application
;;

(def app
  (cqrs-api
    {:info {:info {:title "Kekkonen"}}
     :core {:handlers {:api {:item [#'get-items #'add-item! #'reset-items!]
                             :calculator [#'plus #'times #'inc!]
                             :security #'get-user
                             :system [#'ping #'pong]}}
            :context {:components {:db (atom {})
                                   :ids (atom 0)
                                   :counter (atom 0)}}
            :user {::roles require-roles}}
     :ring {:transformers [api-key-authenticator]}}))

(defn start []
  (server/run-server #'app {:port 3000}))

(comment
  (start))
