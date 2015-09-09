(ns kekkonen.client.cqrs
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [schema.core :as s]
            [ring.util.http-predicates :as hp]))

(s/defn ^:private action->uri [action :- s/Keyword]
  (str/replace (str/join "/" ((juxt namespace name) action)) #"\." "/"))

(s/defn ^:private action
  ([f client name]
    (action f client name {}))
  ([f client name data]
    (let [uri (str (:url client) "/" (action->uri name))
          request (merge
                    (:request client)
                    {:query-params (merge (:data client) data)})]
      (f uri request))))

;;
;; Public api
;;

(def success? hp/ok?)
(def failure? hp/bad-request?)
(def error? hp/internal-server-error?)

(defn create [url]
  {:url url
   :data nil
   :request {:as :transit+json
             :throw-exceptions false
             :content-type :transit+json}})

(def query (partial action http/get))
(def command (partial action http/post))
