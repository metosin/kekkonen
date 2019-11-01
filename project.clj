(defproject metosin/kekkonen-parent "0.5.0-SNAPSHOT"
  :description "A lightweight, remote api library for Clojure."
  :url "https://github.com/metosin/kekkonen"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v20.html"}

  :managed-dependencies [[metosin/kekkonen "0.5.0-SNAPSHOT"]
                         [metosin/kekkonen-core "0.5.0-SNAPSHOT"]

                         [prismatic/plumbing "0.5.4"]
                         [prismatic/schema "1.1.6"]
                         [frankiesardo/linked "1.2.9"]

                         ;; http-stuff, separate module?
                         [metosin/ring-swagger "0.24.0"]
                         [metosin/ring-swagger-ui "2.2.10"]
                         [metosin/ring-http-response "0.9.0"]
                         [ring-middleware-format "0.7.4"]
                         [metosin/muuntaja "0.3.1"]
                         [ring/ring-defaults "0.3.0"]

                         ;; client stuff, separate module?
                         [clj-http "2.3.0"]]
  :profiles {:dev {:plugins [[lein-cloverage "1.0.10"]
                             [lein-midje "3.2.1"]]
                   :source-paths ["dev-src" "modules/kekkonen-core/src"]
                   :dependencies [[metosin/kekkonen]

                                  [org.clojure/clojure "1.10.1"]
                                  [criterium "0.4.4"]
                                  [http-kit "2.2.0"]

                                  ; uploads
                                  [javax.servlet/servlet-api "2.5"]
                                  [midje "1.9.9"]]}
             :perf {:jvm-opts ^:replace ["-Dclojure.compiler.direct-linking=true"]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}}
  :deploy-repositories [["releases" :clojars]]
  :aliases {"all" ["with-profile" "dev:dev,1.8:dev,1.9"]
            "perf" ["with-profile" "default,dev,perf"]
            "test-ancient" ["midje"]
            "coverage" ["cloverage" "--runner" ":midje"]})
