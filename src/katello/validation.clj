(ns katello.validation
  (:refer-clojure :exclude [fn])
  (:require [clojure.string :as string])
  (:use [slingshot.slingshot :only [try+]]
        [katello.ui-tasks :only [success?]]
        [katello.tasks :only [same-name]]
        [serializable.fn :only [fn]] 
        [tools.verify :only [verify-that]]))

(defn cant-be-blank-errors
  "Takes collection of keywords like :name and produces map entry like
   :name-cant-be-blank #\"Name can't be blank"
  [coll]
  (same-name identity
             (comp re-pattern
                   string/capitalize
                   #(string/replace % " cant " " can't "))
             (map #(-> (name %) (str "-cant-be-blank") keyword)
                  coll)))

(def
  ^{:doc "All the different validation error messages that Katello
  can throw. The keys are keywords that can be used to refer to this
  type of error, and the values are regexes that match the error
  notification message in the UI. See also expect-error and
  field-validation."}
  validation-errors
  (merge {:name-taken-error #"(Username|Name) has already been taken"
          :name-no-leading-trailing-whitespace #"Name must not contain leading or trailing white space"
          :name-must-not-contain-characters #"Name cannot contain characters other than"
          :name-must-be-unique-within-org #"Name must be unique within one organization" 
          :repository-url-invalid #"Repository url is invalid"
          :start-date-time-cant-be-blank #"Date and Time can't be blank"
          :password-too-short #"Password must be at least"
          :product-must-be-unique-in-org #"Products within an organization must have unique name"}
         (cant-be-blank-errors [:name
                                :repository-url])))

(def trailing-whitespace-strings [ "abc123 ", " ", "abc  1-2-3   "]) 
(def javascript-strings ["<script type=\"text/javascript\">document.write('<b>Hello World</b>'); </script>"])
(def invalid-character-strings [".", "#", "   ]", "xyz%123", "123 abc 5 % b", "+abc123"])
(def invalid-urls ["@$#%$%&%*()[]{}" "https://" "http" "https://blah.com:5:6:7/abc" "http:///" ""])


(defn matching-validation-errors
  "Returns a set of matching known validation errors"
  [m]
  (set (filter (fn [k] (re-find (validation-errors k) (:msg m))) (keys validation-errors))))

(defn expect-error
  "Returns a predicate that will return true when one of the expected
   errors actually appears in the validation result."
  [expected-validation-err]
  (with-meta (fn [result]
               (some #{expected-validation-err} (:validation-errors result)))
    {:type :serializable.fn/serializable-fn
      :serializable.fn/source `(expect-error ~expected-validation-err)}))

(defn field-validation
  "Calls create-fn, which should create some katello entity with the
   given args. Verifies that the results match the given pred."
   [create-fn args pred]
  (let [results (try+
                 (apply create-fn args)
                 (catch [:type :katello.ui-tasks/validation-failed] e
                   (assoc e :validation-errors (matching-validation-errors e))))] 
    (verify-that (pred results))))

(defn duplicate-disallowed
  "Calls create-fn with the given args, twice, and verifies that the
   second call results in a 'Name taken' validation error."
  [create-fn args & [pred]]
  (apply create-fn args)
  (field-validation create-fn args (or pred (expect-error :name-taken-error))))

(defn name-field-required
  "Verifies that when create-fn is called with args, katello gives an
  error message that the name field cannot be blank. create-fn should
  be a function that creates some entity in katello, eg. an org,
  environment, provider, etc. The args provided should cause create-fn
  to not fill in a name field. Example:
   (name-field-required create-organization [''])"
   [create-fn args]
  (field-validation create-fn args (expect-error :name-cant-be-blank)))

;;this should deprecate duplicate-disallowed
(defn verify-2nd-try-fails-with
  "Verify that when f is called with args for the 2nd time, katello
  shows an error message of the type exp-err. See also
  validation-errors."
  [exp-err f & args]
  (apply f args)
  (field-validation f args (expect-error exp-err)))

(defn expect-error-on-action
  "Calls create-fn, which should create some katello entity with the
   given args. Verifies that the results match the given pred. The
   predicate can be a keyword for a matching error, or any predicate
   function."
  [pred create-fn & args]
  (let [results (try+
                 (apply create-fn args)
                 (catch [:type :katello.ui-tasks/validation-failed] e
                   (assoc e :validation-errors (matching-validation-errors e))))
        pred (if (keyword? pred)
               (expect-error pred)
               pred)] 
    (verify-that (pred results))))