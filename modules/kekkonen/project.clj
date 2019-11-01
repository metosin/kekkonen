(defproject metosin/kekkonen "0.5.2-SNAPSHOT"
  :description "A lightweight, remote api library for Clojure."
  :url "https://github.com/metosin/kekkonen"
  :license {:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/metosin/kekkonen"
        :dir "../.."}
  :plugins [[lein-parent "0.3.4"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[metosin/kekkonen-core]])
