(ns kekkonen.core
  (:require [schema.core :as s]
            [plumbing.core :as p]
            [clojure.string :as str]
            [clojure.walk :as w]
            [plumbing.fnk.pfnk :as pfnk]
            [kekkonen.common :as kc])
  (:import [clojure.lang Var IPersistentMap Symbol PersistentVector AFunction]))

;;
;; Definitions
;;

(s/defschema Function
  (s/=> {s/Keyword s/Any} s/Any))

(s/defschema KeywordMap
  {s/Keyword s/Any})

(s/defschema Handler
  "Action handler metadata"
  {:function Function
   :name s/Keyword
   :type s/Keyword
   :ns s/Keyword
   :user KeywordMap
   :description (s/maybe s/Str)
   :input s/Any
   :output s/Any
   (s/optional-key :source-map) {:line s/Int
                                 :column s/Int
                                 :file s/Str
                                 :ns s/Symbol
                                 :name s/Symbol}
   s/Keyword s/Any})

(s/defschema Kekkonen
  {:handlers KeywordMap
   :context KeywordMap
   :transformers [Function]
   :user KeywordMap
   s/Keyword s/Any})

;;
;; Handlers
;;

(s/defn handler [meta :- KeywordMap, f :- Function]
  (vary-meta f merge {:handler true} meta))

(defn handler? [x]
  (and (map? x) (:function x) (:type x)))

;;
;; Type Resolution
;;

(s/defn type-resolver [& types :- [s/Keyword]]
  (fn [meta]
    (reduce
      (fn [_ type]
        (if (or (some-> meta type true?) (some-> meta :type (= type)))
          (reduced (-> meta (assoc :type type) (dissoc type)))))
      nil types)))

(def default-type-resolver (type-resolver :handler))

;;
;; Collecting
;;

(s/defn ^:private user-meta [meta :- KeywordMap]
  (dissoc
    meta
    ; reserved kekkonen handler stuff
    :type :input :output :description
    ; clojure var meta
    :line :column :file :name :ns :doc
    ; plumbing details
    :schema :plumbing.fnk.impl/positional-info))

(defprotocol CollectHandlers
  (-collect [this type-resolver]))

(s/defn collect
  ([collector]
    (collect collector default-type-resolver))
  ([collector type-resolver]
    (-collect collector type-resolver)))

(defn- -collect-var [v type-resolver]
  (if-let [{:keys [line column file ns name doc schema type] :as meta} (type-resolver (meta v))]
    (if (and name schema)
      {(keyword name) {:function @v
                       :type type
                       :name (keyword name)
                       :user (user-meta meta)
                       :description doc
                       :input (pfnk/input-schema @v)
                       :output (pfnk/output-schema @v)
                       :source-map {:line line
                                    :column column
                                    :file file
                                    :ns (ns-name ns)
                                    :name name}}})
    (throw (ex-info (format "Var %s can't be type-resolved" v) {:target v}))))

(defn- -collect-fn [function type-resolver]
  (if-let [{:keys [name description schema type input output] :as meta} (type-resolver (meta function))]
    (if name
      {(keyword name) {:function function
                       :type type
                       :name (keyword name)
                       :user (user-meta meta)
                       :description (or description "")
                       :input (or (and schema (pfnk/input-schema function)) input s/Any)
                       :output (or (and schema (pfnk/output-schema function)) output s/Any)}})
    (throw (ex-info (format "Function %s can't be type-resolved" function) {:target function}))))

(defn- -collect-ns [ns type-resolver]
  (require ns)
  (some->> ns
           ns-publics
           (map val)
           (filter #(type-resolver (meta %)))
           (map #(-collect-var % type-resolver))
           (apply merge)))

(extend-type AFunction
  CollectHandlers
  (-collect [this type-resolver]
    (-collect-fn this type-resolver)))

(extend-type Var
  CollectHandlers
  (-collect [this type-resolver]
    (-collect-var this type-resolver)))

(extend-type Symbol
  CollectHandlers
  (-collect [this type-resolver]
    (-collect-ns this type-resolver)))

(extend-type PersistentVector
  CollectHandlers
  (-collect [this type-resolver]
    (->> this
         (map #(-collect % type-resolver))
         (apply merge))))

(extend-type IPersistentMap
  CollectHandlers
  (-collect [this type-resolver]
    (p/for-map [[k v] this]
      k (-collect v type-resolver))))

;;
;; Registry
;;

(s/defschema Options
  {:handlers KeywordMap
   (s/optional-key :context) KeywordMap
   (s/optional-key :type-resolver) Function
   (s/optional-key :transformers) [Function]
   (s/optional-key :user) KeywordMap
   s/Keyword s/Any})

(s/def +default-options+ :- Options
  {:handlers {}
   :context {}
   :transformers []
   :type-resolver default-type-resolver
   :user {}})

(s/defn create :- Kekkonen
  "Creates a Kekkonen."
  [options :- Options]
  (let [options (kc/deep-merge +default-options+ options)
        enrich (fn [h m]
                 (if (seq m)
                   (let [ns (->> m (map name) (str/join ".") keyword)]
                     (assoc h :ns ns))
                   (throw (ex-info "can't define handlers into empty namespace" {:handler h}))))
        traverse (fn traverse [x m]
                   (p/for-map [[k v] x]
                     k (if (handler? v)
                         (enrich v m)
                         (traverse v (conj m k)))))]
    {:context (:context options)
     :handlers (traverse (collect (:handlers options) (:type-resolver options)) [])
     :transformers (:transformers options)
     :user (:user options)}))

(s/defn ^:private action-kws [action :- s/Keyword]
  (-> action str (subs 1) (str/split #"[/|\.]") (->> (mapv keyword))))

(s/defn some-handler :- (s/maybe Handler)
  "Returns a handler or nil"
  [kekkonen, action :- s/Keyword]
  (get-in (:handlers kekkonen) (action-kws action)))

(s/defn transform-handlers
  "Applies f to all handlers. If the call returns nil,
  the handler is removed."
  [kekkonen f]
  (merge
    kekkonen
    {:handlers
     (kc/strip-nil-values
       (w/prewalk
         (fn [x]
           (if (handler? x)
             (f x)
             x))
         (:handlers kekkonen)))}))

(s/defn all-handlers :- [Handler]
  "Returns all handlers."
  [kekkonen]
  (let [handlers (atom [])]
    (transform-handlers
      kekkonen
      (fn [handler]
        (swap! handlers conj handler) nil))
    @handlers))

(s/defn invoke
  "Invokes a action handler with the given context."
  ([kekkonen action]
    (invoke kekkonen action {}))
  ([{:keys [transformers] :as kekkonen} action context]
    (if-let [{:keys [function user]} (some-handler kekkonen action)]
      (let [context (kc/deep-merge (:context kekkonen) context)
            context (reduce (fn [context mapper] (mapper context)) context transformers)
            context (reduce
                      (fn [context [k v]]
                        (if-let [mapper (get-in kekkonen [:user k])]
                          (mapper context v)
                          context))
                      context
                      user)
            context (assoc context ::kekkonen kekkonen)]
        (function context))
      (throw (ex-info (str "Invalid action " action) {})))))

(defn with-context [kekkonen context]
  (update-in kekkonen [:context] kc/deep-merge context))

(s/defn context-copy
  "Returns a function that assocs in a value from to-kws path into from-kws in a context"
  [from :- [s/Any], to :- [s/Any]]
  (fn [context]
    (assoc-in context to (get-in context from {}))))

(defn context-dissoc [from-kws]
  "Returns a function that dissocs in a value from from-kws in a context"
  (fn [context]
    (kc/dissoc-in context from-kws)))
