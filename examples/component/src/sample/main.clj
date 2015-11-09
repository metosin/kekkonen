(ns sample.main
  (:require [reloaded.repl :refer [set-init! go]])
  (:gen-class))

(defn -main [& [port]]
  (let [port (or port 3000)]
    (require 'sample.system)
    (set-init! #((resolve 'sample.system/new-system) {:http {:port port}}))
    (go)))
