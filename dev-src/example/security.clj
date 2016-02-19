(ns example.security
  (:require [clojure.set :as set]))

(defn api-key-authenticator [context]
  (let [api-key (-> context :request :query-params :api_key)
        user (condp = api-key
               "seppo" {:name "Seppo" :roles #{}}
               "admin" {:name "Sirpa" :roles #{:admin}}
               nil)]
    (assoc context :user user)))

(defn require-roles [required]
  (fn [context]
    (let [roles (-> context :user :roles)]
      (if (seq (set/intersection roles required))
        context))))
