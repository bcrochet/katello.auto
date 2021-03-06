(ns katello.tasks
  (:use slingshot.slingshot
        [clojure.string :only [split join]]))

(def library "Library")                 

;;var for synchronizing promotion calls, since only one can be done in
;;the system at a time.
(def promotion-lock nil)


(defmacro ^{:see-also "https://github.com/scgilardi/slingshot"}
  expecting-error
  "Inverts exception handling. Execute forms, if error is caught
   matching selector, nil is returned. If no error is caught,
   an :unexpected-success error is thrown. If a non-matching error
   occurs, the exception will not be caught and will propagate up the
   stack.
   Examples: (expecting-error ArithmeticException (/ 1 0)) -> nil

             (expecting-error ArithmeticException (/ 12 3)) ->
               throws :unexpected-success exception.
    See also
   slingshot.slingshot/try+ for selector documentation"
  [selector & forms]
  `(try+ ~@forms
         (throw+ {:type :unexpected-success :expected ~selector})
         (catch ~selector e# nil)))


(defn timestamps
  "Returns an infinite lazy sequence of timestamps in ms, starting
  with the current time, incrementing the time by one on each
  successive item."
  []
  (iterate inc (System/currentTimeMillis)))

(defn unique-names
  "Returns an infinite lazy sequence of timestamped strings, uses s as
  the base string."
  [s]
  (for [t (timestamps)] (str s "-" t)))

(defn uniqueify
  "Returns one unique string using s as the base string.
   Example: (unique-name 'joe') -> 'joe-12694956934'"
  [s]
  (first (unique-names s)))

(defmacro with-unique
  "Binds variables to unique strings. Example:
   (with-unique [x 'foo' y 'bar'] [x y]) will give something like:
     ['foo-12346748964356' 'bar-12346748964357']"
  [bindings & forms]
  `(let ~(vec (apply concat
                 (for [[k v] (apply hash-map bindings)]
                   [k `(uniqueify ~v)])))
     ~@forms))


(defn same-name
  "takes a collection of keywords like :registration-settings and
   returns a mapping like :registration-settings -> 'Registration
   Settings'"
  ([coll] (same-name identity identity coll))
  ([word-fn coll] (same-name word-fn identity coll))
  ([word-fn val-fn coll]
     (zipmap coll (for [keyword coll]
                    (->>
                     (-> keyword name (split #"-"))
                     (map word-fn) (join " ") val-fn)))))


(defn kw-to-text
  "Convert a keyword to plain text, eg, :hi-there 'Hi There'. Passes
   each word through word-fn, and the entire text through val-fn."
  ([kw] (kw-to-text kw identity identity))
  ([kw word-fn] (kw-to-text kw word-fn identity))
  ([kw word-fn val-fn] (->> (-> kw name (split #"-"))
                          (map word-fn) (join " ") val-fn)))