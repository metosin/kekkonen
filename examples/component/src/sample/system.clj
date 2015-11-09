(ns sample.system
  (:require [com.stuartsierra.component :as component]
            [palikka.components.http-kit :as http-kit]
            [sample.handler :as handler]))

(defn new-system [config]
  (component/map->SystemMap
    {:state (reify component/Lifecycle
              (start [_] {:counter (atom 0)}))
     :http (component/using
             (http-kit/create
               (:http config)
               {:fn
                (if (:dev-mode? config)
                  ; re-create handler on every request
                  (fn [system] #((handler/create system) %))
                  handler/create)})
             [:state])}))
