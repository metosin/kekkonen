(defproject metosin/kekkonen "0.0.1-SNAPSHOT"
  :description "Kekkonen, a lightweight CQRS frontend for Clojure"
  :url "https://github.com/metosin/kekkonen"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[prismatic/plumbing "0.4.4"]
                 [prismatic/schema "0.4.4"]
                 [metosin/ring-swagger "0.21.0"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [metosin/ring-http-response "0.6.5"]]
  :profiles {:dev {:plugins [[lein-midje "3.1.3"]]
                   :dependencies [[org.clojure/clojure "1.7.0"]
                                  [midje "1.7.0"]]}}
  :aliases {"test-ancient" ["midje"]})
