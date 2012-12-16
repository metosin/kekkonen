(defproject meve "0.1.0-SNAPSHOT"
  :description "metosin event sourcing"
  :dependencies [[org.clojure/clojure "1.5.0-beta1"]]
  :profiles {:dev {:dependencies [[midje "1.4.0" :exclusions [org.clojure/clojure]]]
                   :plugins [[lein-midje "2.0.0"]
                             [lein-pedantic "0.0.5"]]}}
  :main meve.server
  :repl-options {:init-ns meve.server}
  :min-lein-version "2.0.0")

