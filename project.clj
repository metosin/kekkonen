(defproject metosin/kekkonen "0.4.0"
  :description "A lightweight, remote api library for Clojure."
  :url "https://github.com/metosin/kekkonen"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[prismatic/plumbing "0.5.4"]
                 [prismatic/schema "1.1.6"]
                 [frankiesardo/linked "1.2.9"]

                 ;; http-stuff, separate module?
                 [metosin/ring-swagger "0.24.0"]
                 [metosin/ring-swagger-ui "2.2.10"]
                 [metosin/ring-http-response "0.9.0"]
                 [ring-middleware-format "0.7.2"]
                 [metosin/muuntaja "0.3.1"]
                 [ring/ring-defaults "0.3.0"]

                 ;; client stuff, separate module?
                 [clj-http "2.3.0"]]
  :profiles {:dev {:plugins [[lein-midje "3.2.1"]]
                   :source-paths ["dev-src" "src"]
                   :dependencies [[org.clojure/clojure "1.8.0"]
                                  [criterium "0.4.4"]
                                  [http-kit "2.2.0"]
                                  ; uploads
                                  [javax.servlet/servlet-api "2.5"]
                                  [midje "1.8.3"]]}
             :perf {:jvm-opts ^:replace ["-Dclojure.compiler.direct-linking=true"]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}}
  :deploy-repositories [["releases" :clojars]]
  :aliases {"all" ["with-profile" "dev:dev,1.7"]
            "perf" ["with-profile" "default,dev,perf"]
            "test-ancient" ["midje"]})
