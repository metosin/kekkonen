(ns sample.main
  (:require [com.stuartsierra.component :as component]
            [reloaded.repl :refer [set-init! go]]
            [schema.core :as s])
  (:gen-class))

(defn -main [& [port]]
  (let [port (if port (Integer/parseInt port) 3000)]
    (require 'sample.system)
    (set-init! #((resolve 'sample.system/new-system) {:port port}))
    (go) (println "server running in port" port)))
