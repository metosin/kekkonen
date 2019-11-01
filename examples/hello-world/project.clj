(defproject sample "0.1.0-SNAPSHOT"
  :description "Hello World with Kekkonen"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [http-kit "2.1.19"]
                 [metosin/kekkonen "0.5.1-SNAPSHOT"]]
  :repl-options {:init-ns sample.handler})
