;; The ClojureScript analyzer and compiler can be easily explored from Clojure.
;; This tutorial was designed with Light Table in mind. Evaluate the forms in
;; this file one by one by placing your cursor after each expression and pressing
;; Command-ENTER.

(ns hello-cljsc.core
  (:require
    [clojure.pprint :as pp]
    [clojure.tools.reader :as reader]
    [clojure.tools.reader.reader-types :as readers]
    [cljs.analyzer :as ana]
    [cljs.compiler :as c]
    [cljs.env :as env])
  (:import [java.io StringReader]))

;; ==============================================================================
;; Utilities

;; First we define a series of utility helper functions which will simplify
;; our interactions with the ClojureScript analyzer and compiler.

;; A simple helper to emit emit ClojureScript compiled to JavaScript
;; as a string.
(defn emit-str [ast]
  (with-out-str (c/emit ast)))

;; A simple helper which allows us to read ClojureScript source from a string
;; instead of having to bother with files.
(defn string-reader [s]
  (clojure.lang.LineNumberingPushbackReader. (java.io.StringReader. s)))

;; A simple helper that takes a stream and returns a lazy sequences of
;; read forms.
(defn forms-seq [stream]
  (let [rdr (readers/indexing-push-back-reader stream 1)
        forms-seq* (fn forms-seq* []
                      (lazy-seq
                        (if-let [form (reader/read rdr nil nil)]
                          (cons form (forms-seq*)))))]
    (forms-seq*)))

;; ==============================================================================
;; Reading

;; What other languages call "parsing", Clojure and ClojureScript (like Lisps
;; before them) we call "reading". Reading a string will result in Clojure
;; data structures. These data structures just happen to represent source code!

;; Getting a seq of s-expressions.
(forms-seq (string-reader "(+ 1 2)"))

;; Evaluate the following expressions.

;; form-seq will return a seq containing two forms.
(forms-seq (string-reader "(+ 1 2) (+ 3 4)"))

;; The first first form is (+ 1 2)
(first (forms-seq (string-reader "(+ 1 2) (+ 3 4 )")))

;; The first form is a list.
(first (forms-seq (string-reader "(fn [x y]\n(+ x y))")))

;; The first form in (fn [x y] (+ x y)) is a symbol
(ffirst (forms-seq (string-reader "(fn [x y]\n(+ x y))")))

;; The second form in (fn [x y] (+ x y)) is a vector
(second (first (forms-seq (string-reader "(fn [x y]\n(+ x y))"))))

;; The reader will annotate the data structure via metadata with source line
;; and column information. The presence of this information enables accurate
;; source mapping.

;; On what line and column did we read (fn [x y] (+ x y)) ?
(meta (first (forms-seq (string-reader "(fn [x y]\n(+ x y))"))))

;; On what line and column did we read [x y] ?
(-> (string-reader "(fn [x y]\n(+ x y))")
  forms-seq
  first
  second
  meta)

;; on what line and column did we read (+ x y) ?
(-> (forms-seq (string-reader "(fn [x y]\n(+ x y))"))
  first
  rest
  rest
  first
  meta)

;; =============================================================================
;; Analyzing

;; Lisp forms while adequate for many kinds of user-level syntax manipulation
;; isn't quite rich enough for actually running programs. Thus we'll want to
;; generate an Abstract Syntax Tree (AST) from the forms we have read.

;; First we need to setup a basic analyzer environment.
(def user-env '{:ns {:name cljs.user} :locals {}})

;; A helper to just read the first s-expression
(defn read1 [str]
  (first (forms-seq (string-reader str))))

(read1 "[1 2 3]")

;; cljs.analyzer/analyze takes an analyzer environment and a form, it will
;; return ClojureScript AST node. ClojureScript AST nodes are represented as
;; simple maps. For the following part open the console in a tab so it's easier
;; to view the pretty printed output.

;; This will pretty print a :vector AST node.
(let [form (read1 "[1 2 3]")]
  (pp/pprint (ana/analyze user-env form)))

;; This will pretty print an :invoke AST node.
(let [form (read1 "(foo 1)")]
  (pp/pprint (ana/analyze user-env form)))

;; Before moving any further let's the steps to go from a string to a ClojureScript
;; AST node.

;; First we read a string converting text into forms.
(read1 "(if x true false)")

;; The very first element in the form (if x true false) is a symbol
(first (read1 "(if x true false)"))

;; In Lisp source code, the first element of an s-expression (form) like (foo 1 2)
;; is extremely important. The first element determines where it is a special form
;; as in the case of (if x true false), a macro as in the case of (and true false),
;; or a function call as in the case of (first '(1 2 3)).

;; Special forms are actually handled by the compiler. Macros allows users to extend
;; the language without needing to be a Lisp compiler hacros. Macros will desugar into
;; special forms.

;; If the ClojureScript compiler when it encounters and s-expression with a special
;; form call the cljs.analyer/parse multimethod
(let [form (read1 "(if x true false)")]
  (pp/pprint (ana/parse (first form) user-env form nil)))

;; The following is copy and pasted from analyzer.clj
;;
;; (defmethod parse 'if
;;   [op env [_ test then else :as form] name]
;;   (when (< (count form) 3)
;;     (throw (error env "Too few arguments to if")))
;;   (let [test-expr (disallowing-recur (analyze (assoc env :context :expr) test))
;;         then-expr (analyze env then)
;;         else-expr (analyze env else)]
;;     {:env env
;;      :op :if
;;      :form form
;;      :test test-expr
;;      :then then-expr
;;      :else else-expr
;;      :unchecked @*unchecked-if*
;;      :children [test-expr then-expr else-expr]}))

;; cljs.analyzer/analyze delegates to cljs.analyzer/parse
(let [form (read1 "(if x true false)")]
  (pp/pprint (ana/analyze user-env form)))

;; =============================================================================
;; Compiling

;; to compile an AST node to JavaScript we just call cljs.compiler/emit
;; with an AST node as the argument
(let [form (read1 "(if x true false)")]
  (with-out-str (c/emit (ana/analyze user-env form))))

;; pretty simple! try different things!
(let [form (read1 "(fn [a b] (+ a b))")]
  (with-out-str (c/emit (ana/analyze user-env form))))

;; =============================================================================
;; Macros

(read1 "(and true (diverge))")

(let [form (read1 "(and true (diverge))")]
  (pp/pprint (ana/macroexpand-1 user-env form)))

(let [form (read1 "(+ 1 2 3 4 5 6)")]
  (c/emit (ana/analyze user-env form)))

(let [form (read1 "(apply + [1 2 3 4 5 6])")]
  (c/emit (ana/analyze user-env form)))

(let [form (read1 "(+ 1 (bit-shift-left 16 1))")]
  (c/emit (ana/analyze user-env form)))

(let [form (read1 "(let [arr (array)] (aset arr 0 100))")]
  (c/emit (ana/analyze user-env form)))

;; =============================================================================
;; Type Inference

;; The ClojureScript compiler has some simple type inference to aid with both
;; performance and some rudimentary type checking.

(let [form (read1 "(let [x true] true)")]
  (c/infer-tag (ana/analyze user-env form)))

;; a bug! anyone want to help fix it?
(let [form (read1 "(and true false)")]
  (c/infer-tag (ana/analyze user-env form)))

(let [form (read1 "(if ^boolean (and true false) true false)")]
  (c/emit (ana/analyze user-env form)))

;; =============================================================================
;; Using analysis

;; The ClojureScript compiler generally discards most of the AST once it has
;; emitted JavaScript. However for optimizations, error checking, and supporting
;; external tools, the ClojureScript compiler needs to preserve information about
;; top level definitions encountered in a namespace. This is accomplished by
;; writing into an atom that represents the compilation environment.

;; We define a compilation environment to store analyzer information.
(def cenv (atom {}))

;; If we want to record analyzer information we need to wrap our analyze
;; calls with cljs.env/with-compiler-env
(let [form (read1 "(def x :foo)")]
  (env/with-compiler-env cenv
    (ana/analyze user-env form)))

;; Now if we look at the contents of cenv we'll see that we have a single def for x.
@cenv

;; Let's analyze a top level function.
(let [form (read1 "(defn foo [a b] (+ a b))")]
  (env/with-compiler-env cenv
    (ana/analyze user-env form)))

;; Let's look at just the information for cljs.user/foo. You will see that the
;; analyzer saves quite a bit more information for functions. This is useful
;; for optimizations!
(get-in @cenv [::ana/namespaces 'cljs.user :defs 'foo])

;; Let's redefine foo, this time with two arities.
(let [form (read1 "(defn foo ([a] a) ([a b] (+ a b)))")]
  (env/with-compiler-env cenv
    (c/emit (ana/analyze user-env form))))

;; =============================================================================
;; Using analysis

;; When you evalute this notice that the generated JavaScript is suboptimal.
;; First it invokes cljs.user/foo through JavaScript's call which will be slower
;; on engines. Also by going through call we will need to examine the arguments
;; objects to determine which arity to invoke, another performance hit. This
;; seems a bit silly given that we saw above that the analyzer records enough
;; information to optimize this case.
(let [form (read1 "(defn bar [] (foo 1))")]
  (emit-str
    (env/with-compiler-env cenv
      (ana/analyze user-env form))))

;; And in fact ClojureScript does optimize this case under the :advanced
;; compilation mode. You can get the same behavior by dynamically binding
;; cljs.analyzer/*cljs-static-fns* to true.
(let [form (read1 "(defn bar [] (foo 1))")]
  (binding [ana/*cljs-static-fns* true]
    (emit-str
      (env/with-compiler-env cenv
        (ana/analyze user-env form)))))