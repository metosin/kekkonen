(defproject sample "0.1.0-SNAPSHOT"
  :description "Kekkonen with Component sample"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [http-kit "2.1.19"]
                 [com.stuartsierra/component "0.3.0"]
                 [org.danielsz/system "0.1.9"]
                 [metosin/kekkonen "0.1.0-SNAPSHOT"]]
  :profiles {:dev {:dependencies [[reloaded.repl "0.2.0"]]}
       	     :uberjar {:aot [sample.main]
                       :main sample.main
                       :uberjar-name "sample.jar"}})
