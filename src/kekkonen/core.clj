(ns kekkonen.core
  (:require [schema.core :as s]
            [plumbing.core :as p]
            [clojure.string :as str]
            [plumbing.map :as pm]
            [kekkonen.common :as kc]
            [linked.core :as linked]
            [clojure.walk :as walk]
            [schema.coerce :as sc]
            [schema.utils :as su])
  (:import [clojure.lang Var IPersistentMap Symbol PersistentVector AFunction Keyword]
           [java.io Writer])
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
   :description (s/maybe s/Str)

   ;; extra meta-data
   :user KeywordMap
   (s/optional-key :ns-user) [KeywordMap]
   (s/optional-key :all-user) [KeywordMap]

   ;; input schemas
   ; full input
   :input s/Any
   ; direct handler input
   :handler-input s/Any
   ; user-meta defined input
   :user-input s/Any

   ;; output schemas
   :output s/Any

   (s/optional-key :source-map) {:line s/Int
                                 :column s/Int
                                 :file s/Str
                                 :ns s/Symbol
                                 :name s/Symbol}
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
;; Exposing handler meta-data
;;

(defn stringify-schema [schema]
  (walk/prewalk
    (fn [x]
      (if-not (or (and (map? x) (not (record? x))) (vector? x) (string? x) (keyword? x) (nil? x))
        (pr-str x) x))
    schema))

; TODO: pass Schemas as-is -> implement https://github.com/metosin/web-schemas
(s/defn public-handler
  [handler :- Handler]
  (some-> handler
          (select-keys [:input :name :ns :output :source-map :type :action])
          (update-in [:input] stringify-schema)
          (update-in [:output] stringify-schema)))

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
    (if-let [{:keys [name description type] :as meta} (type-resolver (meta this))]
      (let [{:keys [input output]} (kc/extract-schema this)]
        (if name
          {(namespace
             {:name (keyword name)})
           {:function this
            :type type
            :name (keyword name)
            :user (user-meta meta)
            :description (or description "")
            :input input
            :output output}}))
      (throw (ex-info (format "Function %s can't be type-resolved" this) {:target this})))))

(extend-type Var
  Collector
  (-collect [this type-resolver]
    (if-let [{:keys [line column file ns name doc type] :as meta} (type-resolver (meta this))]
      (let [{:keys [input output]} (kc/extract-schema this)]
        {(namespace
           {:name (keyword name)})
         {:function @this
          :type type
          :name (keyword name)
          :user (user-meta meta)
          :description doc
          :input input
          :output output
          :source-map {:line line
                       :column column
                       :file file
                       :ns (ns-name ns)
                       :name name}}})
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

(s/defrecord Dispatcher
  [handlers :- {s/Keyword Handler}
   context :- KeywordMap
   coercion :- {:input (s/maybe KeywordMap)
                :output s/Any}
   transformers :- [Function]
   user :- KeywordMap])

(defmethod clojure.core/print-method Dispatcher
  [_ ^Writer writer]
  (.write writer "#<Dispatcher>"))

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

;;
;; coercion
;;

(def ^:private memoized-coercer (memoize sc/coercer))

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

(defn simple-coercion [matcher]
  (fn [context schema]
    (coerce! schema matcher context nil ::request)))

(defn multi-coercion [key->coercion]
  (let [coercions (pm/flatten key->coercion)]
    (fn [context schema]
      (reduce
        (fn [ctx [ks coercion]]
          (if-let [coercion-schema (get-in schema ks)]
            (assoc-in ctx ks (coercion coercion-schema (get-in ctx ks)))
            ctx))
        context coercions))))

(defn input-coerce!
  ([context schema]
    ;; TODO: ensure that dispatcher is always present, also for transformers.
   (if-let [dispatcher (get-dispatcher context)]
     (input-coerce! context schema (-> dispatcher :coercion :input))
     (throw (ex-info "no attached dispatcher." {}))))
  ([context schema key->matcher]
   (if-not (kc/any-map-schema? schema)
     (as-> context context
           (if-let [coercion (::coercion context)]
             (coercion context schema)
             context)
           (if key->matcher
             (reduce-kv
               (fn [ctx k matcher]
                 (let [schema (select-keys schema [k])
                       schema (if (seq schema) schema s/Any)]
                   (merge ctx (coerce! schema matcher (select-keys ctx [k]) nil ::request))))
               context key->matcher)
             context))
     context)))

;;
;; Dispatching to handlers
;;

(s/defn some-handler :- (s/maybe Handler)
  "Returns a handler or nil"
  [dispatcher, action :- s/Keyword]
  (get-in dispatcher [:handlers action]))

(defn- dispatch [dispatcher mode action context]
  (if-let [{:keys [function all-user input output] :as handler} (some-handler dispatcher action)]
    (let [input-matcher (-> dispatcher :coercion :input)
          context (as-> context context

                        ; TODO: transformers -> middleware/interceptors

                        ;; base-context from Dispatcher
                        (kc/deep-merge (:context dispatcher) context)

                        ;; run all the transformers
                        ;; short-circuit execution if a transformer returns nil
                        (reduce (fn [ctx mapper] (or (mapper ctx) (reduced nil))) context (:transformers dispatcher))

                        ; TODO: type-transformers?

                        ;; run all the user transformers per namespace/handler
                        ;; start from the root. a returned nil context short-circuits
                        ;; the run an causes ::dispatch error. Apply local coercion
                        ;; in the input is defined (using same definitions as with handlers)
                        ; TODO: precompile for fail-fast & speed? input-coerce only tagged ones? test!
                        (reduce
                          (fn [ctx [k v]]
                            (if-let [mapper-gen (get-in dispatcher [:user k])]
                              (let [mapper (mapper-gen v)
                                    input-schema (:input (kc/extract-schema mapper))
                                    ctx (input-coerce! ctx input-schema input-matcher)]
                                (or (mapper ctx) (reduced nil)))
                              ctx))
                          context
                          (apply concat all-user))

                        ;; run context coercion for :validate|:invoke and if context coercion is set
                        (cond-> context (and context (#{:validate :invoke} mode))
                                ((fn [ctx] (input-coerce! ctx input input-matcher))))

                        ;; inject in stuff the context if not nil
                        (cond-> context context (merge context {::dispatcher dispatcher
                                                                ::handler handler})))]

      (when-not context
        (throw (ex-info (str "Invalid action") {:type ::dispatch, :value action})))

      ;; all good, let's invoke?
      (if (#{:invoke} mode)
        (let [response (function context)]
          ;; response coercion
          (if (and output (-> dispatcher :coercion :output))
            (coerce! output (-> dispatcher :coercion :output) response nil ::response)
            response))))
    (throw (ex-info (str "Invalid action") {:type ::dispatch, :value action}))))

(s/defn check
  "Checks an action handler with the given context."
  ([dispatcher :- Dispatcher, action :- s/Keyword]
    (check dispatcher action {}))
  ([dispatcher :- Dispatcher, action :- s/Keyword, context :- Context]
    (dispatch dispatcher :check action context)))

(s/defn validate
  "Checks if context is valid for the handler (without calling the body).
  Returns nil or throws an exception."
  ([dispatcher :- Dispatcher, action :- s/Keyword]
    (validate dispatcher action {}))
  ([dispatcher :- Dispatcher, action :- s/Keyword, context :- Context]
    (dispatch dispatcher :validate action context)))

(s/defn invoke
  "Invokes an action handler with the given context."
  ([dispatcher :- Dispatcher, action :- s/Keyword]
    (invoke dispatcher action {}))
  ([dispatcher :- Dispatcher, action :- s/Keyword, context :- Context]
    (dispatch dispatcher :invoke action context)))

;;
;; Listing handlers
;;

(defn- filter-by-path [handlers path]
  (if-not path
    handlers
    (seq
      (filter
        (fn [{:keys [ns]}]
          (if ns
            (let [path-seq (str/split (subs (str path) 1) #"[\.]")
                  action-seq (str/split (subs (str ns) 1) #"[\.]")]
              (= path-seq (take (count path-seq) action-seq)))
            true))
        handlers))))

(defn- map-handlers [dispatcher mode prefix context success failure]
  (-> dispatcher
      :handlers
      vals
      (filter-by-path prefix)
      (->>
        (map
          (fn [handler]
            (try
              (when-not (= mode :all)
                (dispatch dispatcher mode (:action handler) context))
              [handler (success handler)]
              (catch Exception e
                (if (-> e ex-data :type (= ::dispatch))
                  [nil nil]
                  [handler (failure e)])))))
        (filter first)
        (into {}))))

(s/defn all-handlers :- [Handler]
  "Returns all handlers filtered by namespace"
  [dispatcher :- Dispatcher
   prefix :- (s/maybe s/Keyword)]
  (keep second (map-handlers dispatcher :all prefix {} identity (constantly nil))))

(s/defn available-handlers :- [Handler]
  "Returns all available handlers based on namespace and context"
  [dispatcher :- Dispatcher
   prefix :- (s/maybe s/Keyword)
   context :- Context]
  (keep first (map-handlers dispatcher :check prefix context identity (constantly nil))))

(s/defn dispatch-handlers :- {Handler s/Any}
  "Returns a map of action -> errors based on mode, namespace and context."
  [dispatcher :- Dispatcher
   mode :- (s/enum :check :validate)
   prefix :- (s/maybe s/Keyword)
   context :- Context]
  (map-handlers dispatcher mode prefix context (constantly nil) ex-data))

;;
;; Creating a Dispatcher
;;

(s/defschema Options
  {:handlers {(s/cond-pre s/Keyword Namespace) s/Any}
   (s/optional-key :context) KeywordMap
   (s/optional-key :type-resolver) Function
   (s/optional-key :transformers) [Function]
   (s/optional-key :coercion) {(s/optional-key :input) (s/maybe KeywordMap)
                               (s/optional-key :output) s/Any}
   (s/optional-key :user) (s/cond-pre [[(s/one s/Keyword 'key) Function]] KeywordMap)
   s/Keyword s/Any})

(s/def +default-options+ :- Options
  {:handlers {}
   :context {}
   :transformers []
   :coercion {:input {:data (constantly nil)}
              :output (constantly nil)}
   :type-resolver default-type-resolver
   :user {}})

(defn- collect-and-enrich
  [{:keys [handlers type-resolver user]} allow-empty-namespaces?]
  (let [handler-ns (fn [m] (if (seq m) (->> m (map :name) (map name) (str/join ".") keyword)))
        collect-ns-meta (fn [m] (if (seq m) (->> m (map :meta) (filterv (complement empty?)))))
        handler-action (fn [n ns] (keyword (str/join "/" (map name (filter identity [ns n])))))
        reorder (fn [m]
                  (let [ordered (into
                                  (linked/map)
                                  (keep
                                    (fn [k]
                                      (if-let [v (m k)]
                                        [k v]))
                                    (keys user)))]
                    ordered))
        enrich (fn [h m]
                 (if (or (seq m) allow-empty-namespaces?)
                   (let [ns (handler-ns m)
                         ns-user (collect-ns-meta m)
                         user-meta (:user h)
                         all-user (map reorder (if-not (empty? user-meta) (conj ns-user user-meta) ns-user))
                         user-input (reduce
                                      (fn [acc [k v]]
                                        (if-let [f (user k)]
                                          (let [schema (:input (kc/extract-schema (f v)))]
                                            (kc/merge-map-schemas acc schema))
                                          acc)) {} (apply concat all-user))
                         input (kc/merge-map-schemas (:input h) user-input)]
                     (merge h {:ns ns
                               :ns-user ns-user
                               :all-user all-user

                               :handler-input (:input h)
                               :user-input user-input
                               :input (if (seq input) input s/Any)

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
  "Creates a Dispatcher"
  [options :- Options]
  (let [options (-> options
                    (->> (kc/deep-merge +default-options+))
                    (update :user (fn [user]
                                    (if-not (map? user)
                                      (apply linked/map (apply concat user))
                                      user))))
        handlers (->> (collect-and-enrich options false))]
    (map->Dispatcher
      (merge
        (select-keys options [:context :transformers :coercion :user])
        {:handlers handlers}))))

(s/defn transform-handlers
  "Applies f to all handlers. If the call returns nil,
  the handler is removed."
  [dispatcher :- Dispatcher, f :- Function]
  (update-in dispatcher [:handlers] (fn [handlers]
                                      (->> handlers
                                           (p/map-vals f)
                                           (filter (p/fn-> second))
                                           (into {})))))

(s/defn inject
  "Injects handlers into an existing Dispatcher"
  [dispatcher :- Dispatcher, handlers]
  (if handlers
    (let [handler (collect-and-enrich
                    (merge dispatcher {:handlers handlers :type-resolver any-type-resolver}) true)]
      (update-in dispatcher [:handlers] merge handler))))
