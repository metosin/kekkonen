(ns sample.main
  (:require [reloaded.repl :refer [set-init! go]])
  (:gen-class))

(defn -main [& [port]]
  (require 'sample.system)
  (set-init! #((resolve 'sample.system/new-system) {:port port}))
  (go) (println "server running in port" port))
