(ns kekkonen.core
  (:require [schema.core :as s]
            [plumbing.core :as p]
            [clojure.string :as str]
            [clojure.walk :as w]
            [plumbing.fnk.pfnk :as pfnk]
            [kekkonen.common :as kc]
            [clojure.walk :as walk]
            [schema.coerce :as sc]
            [schema.utils :as su])
  (:import [clojure.lang Var IPersistentMap Symbol PersistentVector AFunction Keyword])
  (:refer-clojure :exclude [namespace]))

;;
;; Definitions
;;

(s/defschema Function
  (s/=> {s/Keyword s/Any} s/Any))

(s/defschema KeywordMap
  {s/Keyword s/Any})

(s/defschema Context
  (merge
    KeywordMap
    {(s/optional-key :data) s/Any}))

(s/defschema Handler
  {:function Function
   :type s/Keyword
   :name s/Keyword
   :ns (s/maybe s/Keyword)
   :action s/Keyword
   :user KeywordMap
   (s/optional-key :ns-user) [KeywordMap]
   (s/optional-key :all-user) [KeywordMap]
   :description (s/maybe s/Str)
   :input s/Any
   :output s/Any
   (s/optional-key :source-map) {:line s/Int
                                 :column s/Int
                                 :file s/Str
                                 :ns s/Symbol
                                 :name s/Symbol}
   s/Keyword s/Any})

(s/defschema Dispatcher
  {:handlers {s/Keyword Handler}
   :context KeywordMap
   :coercion {:input s/Any
              :output s/Any}
   :transformers [Function]
   :user KeywordMap
   s/Keyword s/Any})

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

(s/defn any-type-resolver [meta]
  (if (:type meta) meta))

(def default-type-resolver (type-resolver :handler))

;;
;; Collecting
;;

(defprotocol Collector
  (-collect [this type-resolver]))

(s/defn collect
  [collector type-resolver]
  (-collect collector type-resolver))

;;
;; Handlers
;;

(s/defn ^:private user-meta [meta :- KeywordMap]
  (dissoc
    meta
    ; reserved dispatcher handler stuff
    :type :input :output :description
    ; clojure var meta
    :line :column :file :name :ns :doc
    ; plumbing details
    :schema :plumbing.fnk.impl/positional-info))

(s/defn handler
  [meta :- KeywordMap, f :- Function]
  (assert (:name meta) "handler should have :name")
  (vary-meta f merge {:type :handler} meta))

(defn handler? [x]
  (and (map? x) (:function x) (:type x)))

;;
;; Namespaces
;;

(s/defrecord Namespace [name :- s/Keyword, meta :- KeywordMap]
  Collector
  (-collect [this _]
    this))

(s/defn namespace [meta :- KeywordMap]
  (->Namespace (:name meta) (dissoc meta :name)))

;;
;; Collection helpers
;;

(extend-type AFunction
  Collector
  (-collect [this type-resolver]
    (if-let [{:keys [name description schema type input output] :as meta} (type-resolver (meta this))]
      (if name
        {(namespace
           {:name (keyword name)})
         {:function this
          :type type
          :name (keyword name)
          :user (user-meta meta)
          :description (or description "")
          :input (or (and schema (pfnk/input-schema this)) input s/Any)
          :output (or (and schema (pfnk/output-schema this)) output s/Any)}})
      (throw (ex-info (format "Function %s can't be type-resolved" this) {:target this})))))

(defn- extract-var-schema [schema-key v]
  (let [pfnk-schema (case schema-key
                      :input pfnk/input-schema
                      :output pfnk/output-schema)
        pfnk? (fn [x]
                (and (satisfies? pfnk/PFnk x)
                     ;; TODO: better way to figure out is this ok
                     (-> x meta :plumbing.fnk.impl/positional-info)))]
    (cond
      (pfnk? @v) (pfnk-schema @v)
      :else (or (schema-key (meta v))))))

(extend-type Var
  Collector
  (-collect [this type-resolver]
    (if-let [{:keys [line column file ns name doc type] :as meta} (type-resolver (meta this))]
      {(namespace
         {:name (keyword name)})
       {:function @this
        :type type
        :name (keyword name)
        :user (user-meta meta)
        :description doc
        :input (extract-var-schema :input this)
        :output (extract-var-schema :output this)
        :source-map {:line line
                     :column column
                     :file file
                     :ns (ns-name ns)
                     :name name}}}
      (throw (ex-info (format "Var %s can't be type-resolved" this) {:target this})))))

(extend-type Symbol
  Collector
  (-collect [this type-resolver]
    (require this)
    (some->> this
             ns-publics
             (map val)
             (filter #(type-resolver (meta %)))
             (map #(-collect % type-resolver))
             (apply merge))))

(extend-type Keyword
  Collector
  (-collect [this _]
    (namespace {:name this})))

(extend-type PersistentVector
  Collector
  (-collect [this type-resolver]
    (->> this
         (map #(-collect % type-resolver))
         (apply merge))))

(extend-type IPersistentMap
  Collector
  (-collect [this type-resolver]
    (p/for-map [[k v] this]
      (-collect k type-resolver) (-collect v type-resolver))))

;;
;; Dispatcher
;;

(s/defschema Options
  {:handlers KeywordMap
   (s/optional-key :context) KeywordMap
   (s/optional-key :type-resolver) Function
   (s/optional-key :transformers) [Function]
   (s/optional-key :coercion) {(s/optional-key :input) s/Any
                               (s/optional-key :output) s/Any}
   (s/optional-key :user) KeywordMap
   s/Keyword s/Any})

(s/def +default-options+ :- Options
  {:handlers {}
   :context {}
   :transformers []
   :coercion {:input (constantly nil)
              :output (constantly nil)}
   :type-resolver default-type-resolver
   :user {}})

(defn- collect-and-enrich
  [handlers type-resolver allow-empty-namespaces?]
  (let [handler-ns (fn [m] (if (seq m) (->> m (map :name) (map name) (str/join ".") keyword)))
        collect-ns-meta (fn [m] (if (seq m) (->> m (map :meta) (filterv (complement empty?)))))
        handler-action (fn [n ns] (keyword (str/join "/" (map name (filter identity [ns n])))))
        enrich (fn [h m]
                 (if (or (seq m) allow-empty-namespaces?)
                   (let [ns (handler-ns m)
                         ns-user (collect-ns-meta m)
                         user-meta (:user h)
                         all-user (if-not (empty? user-meta) (conj ns-user user-meta) ns-user)]
                     (merge h {:ns ns
                               :ns-user ns-user
                               :all-user all-user
                               :action (handler-action (:name h) ns)}))
                   (throw (ex-info "can't define handlers into empty namespace" {:handler h}))))
        traverse (fn traverse [x m]
                   (flatten
                     (for [[k v] x]
                       (if (handler? v)
                         (enrich v m)
                         (traverse v (conj m k))))))]
    (-> handlers
        (collect type-resolver)
        (traverse [])
        (->> (group-by :action)
             (p/map-vals first)))))

(s/defn dispatcher :- Dispatcher
  "Creates a Dispatcher."
  [options :- Options]
  (let [options (kc/deep-merge +default-options+ options)
        handlers (->> (collect-and-enrich (:handlers options) (:type-resolver options) false))]
    (merge
      (select-keys options [:context :transformers :coercion :user])
      {:handlers handlers})))

(s/defn some-handler :- (s/maybe Handler)
  "Returns a handler or nil"
  [dispatcher :- Dispatcher, action :- s/Keyword]
  (get-in dispatcher [:handlers action]))

(s/defn transform-handlers
  "Applies f to all handlers. If the call returns nil,
  the handler is removed."
  [dispatcher :- Dispatcher, f :- Function]
  (update-in dispatcher [:handlers] (comp kc/strip-nil-values (partial p/map-vals f))))

(s/defn inject
  "Injects handlers into an existing Dispatcher"
  [dispatcher :- Dispatcher, handler]
  (let [handler (collect-and-enrich handler any-type-resolver true)]
    (update-in dispatcher [:handlers] merge handler)))

;;
;; Calling handlers
;;

(def memoized-coercer (memoize sc/coercer))

(defn coerce! [schema matcher value in type]
  (let [coercer (memoized-coercer schema matcher)
        coerced (coercer value)]
    (if-not (su/error? coerced)
      coerced
      (throw
        (ex-info
          "Coercion error"
          {:type type
           :in in
           :value value
           :schema schema
           :error coerced})))))

(s/defn ^:private prepare
  "Prepares a context for invocation or validation. Returns an 0-arity function
  or throws exception."
  [dispatcher :- Dispatcher, action :- s/Keyword, context :- Context, invoke? :- s/Bool]
  (if-let [{:keys [function all-user input output] :as handler} (some-handler dispatcher action)]
    (let [context (as-> context context

                        ;; base-context from Dispatcher
                        (kc/deep-merge (:context dispatcher) context)

                        ;; run coercion in invoke? and if coercion-matcher is set
                        (cond-> context (and invoke? input (-> dispatcher :coercion :input))
                                ((fn [context]
                                   (coerce! input (-> dispatcher :coercion :input) context nil ::request))))

                        ;; run all the transformers
                        (reduce
                          (fn [context mapper]
                            (mapper context))
                          context
                          (:transformers dispatcher))

                        ;; run all the user transformers per namespace/handler, start from the root
                        (reduce
                          (fn [context [k v]]
                            (if-let [mapper (get-in dispatcher [:user k])]
                              (mapper context v)
                              context))
                          context
                          (apply concat all-user))

                        ;; inject in stuff the context
                        (merge context {::dispatcher dispatcher
                                        ::handler handler}))]

      ;; all good, let's invoke?
      (if invoke?
        (let [response (function context)]
          ;; TODO: change all transformers into interceptors and run response pipeline here?
          ;; response coercion
          (if (and output (-> dispatcher :coercion :output))
            (coerce! output (-> dispatcher :coercion :output) response nil ::response)
            response))))
    (throw (ex-info (str "Invalid action " action) {}))))

(s/defn invoke
  "Invokes an action handler with the given context."
  ([dispatcher :- Dispatcher, action :- s/Keyword]
    (invoke dispatcher action {}))
  ([dispatcher :- Dispatcher, action :- s/Keyword, context :- Context]
    (prepare dispatcher action context true)))

(s/defn validate
  "Checks if context is valid for the handler (without calling the body).
  Returns nil or throws an exception."
  ([dispatcher :- Dispatcher, action :- s/Keyword]
    (validate dispatcher action {}))
  ([dispatcher :- Dispatcher, action :- s/Keyword, context :- Context]
    (prepare dispatcher action context false)))

;;
;; Listing handlers
;;

(s/defn all-handlers :- [Handler]
  "Returns all handlers."
  ([dispatcher :- Dispatcher]
    (all-handlers dispatcher nil))
  ([dispatcher :- Dispatcher, prefix :- (s/maybe s/Keyword)]
    (let [handlers (atom [])]
      (transform-handlers
        dispatcher
        (fn [handler]
          (swap! handlers conj handler) nil))
      (if-not prefix
        @handlers
        (seq
          (filter
            (fn [{:keys [ns]}]
              (if ns
                (let [prefix-seq (str/split (subs (str prefix) 1) #"[\.]")
                      action-seq (str/split (subs (str ns) 1) #"[\.]")]
                  (= prefix-seq (take (count prefix-seq) action-seq)))
                true))
            @handlers))))))

(s/defn available-handlers :- [Handler]
  "Returns all handlers which are available under a given context"
  ([dispatcher :- Dispatcher, context :- Context]
    (available-handlers dispatcher context nil))
  ([dispatcher :- Dispatcher, context :- Context, prefix :- (s/maybe s/Keyword)]
    (filter
      (fn [handler]
        (try
          (validate dispatcher (:action handler) context)
          true
          (catch Exception _)))
      (all-handlers dispatcher prefix))))

(defn stringify-schema [schema]
  (walk/prewalk
    (fn [x]
      (if-not (or (and (map? x) (not (record? x))) (vector? x) (string? x) (keyword? x))
        (pr-str x) x))
    schema))

; TODO: pass Schemas as-is -> implement https://github.com/metosin/web-schemas
(s/defn public-meta
  [handler :- Handler]
  (some-> handler
          (select-keys [:input :name :ns :output :source-map :type :action])
          (update-in [:input] stringify-schema)
          (update-in [:output] stringify-schema)))

;;
;; Working with contexts
;;

(s/defn get-dispatcher [context :- Context]
  (get context ::dispatcher))

(s/defn get-handler [context :- Context]
  (get context ::handler))

(s/defn with-context [dispatcher :- Dispatcher, context :- Context]
  (update-in dispatcher [:context] kc/deep-merge context))

(s/defn context-copy
  "Returns a function that assocs in a value from to-kws path into from-kws in a context"
  [from :- [s/Any], to :- [s/Any]]
  (s/fn [context :- Context]
    (assoc-in context to (get-in context from {}))))

(s/defn context-dissoc [from-kws :- [s/Any]]
  "Returns a function that dissocs in a value from from-kws in a context"
  (s/fn [context :- Context]
    (kc/dissoc-in context from-kws)))
