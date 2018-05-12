(ns kekkonen.client.cqrs
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [schema.core :as s]
            [kekkonen.common :as kc]
            [ring.util.http-predicates :as hp]))

(s/defn ^:private action->uri [action :- s/Keyword]
  (str/replace (str/join "/" ((juxt namespace name) action)) #"\." "/"))

(defn context [client data]
  (kc/deep-merge client {:data data}))

(def +options+ {:query {:params :query-params, :f http/get}
                :command {:params :body-params, :f http/post}})

(s/defn ^:private action
  ([options client name]
    (action options client name {}))
  ([options client name data]
    (let [{:keys [f params]} options
          uri (str (:url client) "/" (action->uri name))
          request (merge
                    (:request client)
                    {params (merge (:data client) data)})]
      (f uri request))))

;;
;; Public api
;;

(defn create [url]
  {:url url
   :data nil
   :request {:as :transit+json
             :throw-exceptions false
             :content-type :transit+json}})

(def query (partial action (:query +options+)))
(def command (partial action (:command +options+)))

(def success? hp/ok?)
(def failure? hp/bad-request?)
(def error? hp/internal-server-error?)
