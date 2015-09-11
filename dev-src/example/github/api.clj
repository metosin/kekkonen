(ns example.github.api
  (:require [example.github.security :as security]
            [ring.adapter.jetty :as jetty]
            [kekkonen.cqrs :refer :all]
            [plumbing.core :as p]
            [schema.core :as s]))

;;
;; Schemas
;;

(s/defschema Repository
  "A Repository"
  {:id s/Int
   :name s/Str
   :allowed #{s/Str}
   :watchers #{s/Str}
   :stargazers #{s/Str}})

(s/def compojure-api
  {:id 1
   :name "Compojure API"
   :allowed #{"seppo" "sirpa"}
   :watchers #{}
   :stargazers #{}})

;;
;; Commands & Queries
;;

(p/defnk ^:query list-repositorys
  "Retrieves all Repos"
  {:responses {success-status {:schema [Repository]}}}
  [[:components repos]]
  (success (vals @repos)))

(p/defnk ^:query get-repository
  "Retrieves repo details"
  {:responses {success-status {:schema (s/maybe Repository)}}}
  [[:components repos]
   [:data id :- s/Int]]
  (success (@repos id)))

(p/defnk ^:command fork
  "Forks a repo"
  [[:data id :- s/Int]]
  (success {:forked id}))

(p/defnk ^:command watch
  "Watch a repo"
  [user
   [:data id :- s/Int]
   [:components repos]]
  (swap! repos #(update-in % [id :watchers] conj user))
  (success {:forked id}))

(p/defnk ^:command un-watch
  "Watch a repo"
  [user
   [:data id :- s/Int]
   [:components repos]]
  (swap! repos #(update-in % [id :watchers] disj user))
  (success {:forked id}))

(p/defnk ^:command star
  "Watch a repo"
  [user
   [:data id :- s/Int]
   [:components repos]]
  (swap! repos #(update-in % [id :stargazers] conj user))
  (success))

(p/defnk ^:command un-star
  "Watch a repo"
  [user
   [:data id :- s/Int]
   [:components repos]]
  (swap! repos #(update-in % [id :stargazers] disj user))
  (success))

(do
  ;;
  ;; testsing
  ;;

  (p/defnk ^:query ping [] (success {:ping "pong"}))
  (p/defnk ^:query pong [] (success {:pong "ping"}))

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
    (success {:result (swap! counter inc)})))

;;
;; Application
;;

(def app
  (cqrs-api
    {:info {:info {:title "Kekkonen"}}
     :core {:handlers {:api {:github [#'get-repository
                                      #'list-repositorys
                                      #'fork
                                      #'watch
                                      #'un-watch
                                      #'star
                                      #'un-star]
                             :calculator [#'plus #'times #'inc!]
                             :security #'security/get-user
                             :system {:ping [#'ping #'pong]}}}
            :context {:components {:repos (atom {(:id compojure-api)
                                                 compojure-api})
                                   :counter (atom 0)}}
            :user {:roles security/require-roles}}
     :ring {:transformers [security/api-key-authenticator]}}))

;;
;; Main
;;

(defn start []
  (jetty/run-jetty
    #'app
    {:port 3000
     :join? false}))

(comment
  (start))
