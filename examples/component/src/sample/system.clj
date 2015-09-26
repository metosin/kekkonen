(ns sample.system
  (:require [com.stuartsierra.component :as component]
            [system.components.http-kit :as http-kit]
            [sample.handler :as handler]))

(defn new-system [config]
  (let [system (component/map->SystemMap {:counter (atom 0)})]
    (assoc system :http (http-kit/new-web-server (:port config) (handler/new-app system)))))