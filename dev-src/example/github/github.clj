(ns example.github.github
  (:require [example.github.security :as security]
            [org.httpkit.server :as server]
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
  "Unwatch a repo"
  [user
   [:data id :- s/Int]
   [:components repos]]
  (swap! repos #(update-in % [id :watchers] disj user))
  (success {:forked id}))

(p/defnk ^:command star
  "Star a repo"
  [user
   [:data id :- s/Int]
   [:components repos]]
  (swap! repos #(update-in % [id :stargazers] conj user))
  (success))

(p/defnk ^:command un-star
  "Unstar a repo"
  [user
   [:data id :- s/Int]
   [:components repos]]
  (swap! repos #(update-in % [id :stargazers] disj user))
  (success))

(do
  ;;
  ;; testing
  ;;

  (p/defnk ^:query ping [] (success {:ping "pong"}))

  (p/defnk ^:command boss-move
    "For bosses only"
    {:roles #{:boss}}
    [] (success {:all :done}))

  (p/defnk ^:query plus
    {:responses {success-status {:schema {:result s/Int}}}}
    [[:data x :- s/Int, y :- s/Int]]
    (success {:result (+ x y)}))

  (p/defnk ^:command times
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
    {:swagger {:info {:title "Kekkonen"
                      :version "1.0"}}
     :core {:handlers {:api {:github [#'get-repository
                                      #'list-repositorys
                                      #'fork
                                      #'watch
                                      #'un-watch
                                      #'star
                                      #'un-star]
                             :calculator [#'plus #'times #'inc!]
                             :security #'security/get-user
                             :a {:b {:c [#'ping
                                         #'boss-move]}}}}
            :context {:components {:repos (atom {(:id compojure-api)
                                                 compojure-api})
                                   :counter (atom 0)}}
            :meta {:roles security/require-roles}}
     :ring {:interceptors [security/api-key-authenticator]}}))

(comment
  (server/run-server #'app {:port 3000}))
