(ns example.handlers
  (:require [ring.adapter.jetty :as jetty]
            [plumbing.core :as p]
            [ring.util.http-response :refer [ok]]
            [kekkonen.ring :as r]
            [kekkonen.core :as k]
            [kekkonen.middleware :as mw]
            [schema.core :as s]))

(p/defnk ^:handler ping [] (ok {:ping "pong"}))

(p/defnk ^:handler snoop
  [request] (ok (dissoc request :body)))

(p/defnk ^:handler echo
  [[:request [:body-params x :- s/Str]]]
  (ok {:x x}))

(def app
  (mw/api-middleware
    (r/ring-handler
      (k/create
        {:handlers {:api 'example.handlers}}))))

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


