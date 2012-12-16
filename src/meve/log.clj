(ns meve.log
  (:use [clojure.string :only [join]]))

(def levels (zipmap [:trace :debug :info :warn :error] (range)))
(def limit (atom (:trace levels)))
(def time-fmt (org.joda.time.format.DateTimeFormat/forPattern "yyyy/MM/dd HH:mm:ss.SSS"))

(defn log-limit! [level]
  (reset! limit (levels level)))

(defn make-stacktrace [e]
  (if (instance? Throwable e)
    (-> (StringBuilder.)
      (.append (.getName (class e)))
      (.append ": ")
      (.append (.getMessage e))
      (.append \newline)
      (.toString))
    ""))

(defn time-str []
  (.print time-fmt (System/currentTimeMillis)))

(defn make-log-message [file line level & args]
  (-> (StringBuilder.) 
    (.append (format "%-5s %s [%s:%d] " (name level) (time-str) file line))
    (.append (join \space args))
    (.append \newline)
    (.append (make-stacktrace (last args)))
    (.toString)))

(defn log-out [message]
  (print message)
  (flush))

(defmacro -log [level file line & args]
  `(if (>= (levels ~level) (deref limit))
     (log-out (make-log-message ~file ~line ~level ~@args))))

(defmacro log [level & args] `(-log ~level ~*file* ~(:line (meta &form)) ~@args))
(defmacro trace [& args] `(-log :trace ~*file* ~(:line (meta &form)) ~@args))
(defmacro debug [& args] `(-log :debug ~*file* ~(:line (meta &form)) ~@args))
(defmacro info [& args]  `(-log :info  ~*file* ~(:line (meta &form)) ~@args))
(defmacro warn [& args]  `(-log :warn  ~*file* ~(:line (meta &form)) ~@args))
(defmacro error [& args] `(-log :error ~*file* ~(:line (meta &form)) ~@args))
