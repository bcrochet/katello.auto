(ns katello.tests.organizations
  (:use [error.handler :only [with-handlers handle ignore]]
        [com.redhat.qe.verify :only [verify]]
        [test-clj.testng :only [gen-class-testng data-driven]]
        [katello.tests.setup :only [beforeclass-ensure-admin]]
        [katello.conf :only [config]])
  (:require [katello.tasks :as tasks]
            [katello.api-tasks :as api]
            [katello.validation :as validate])
  (:import [org.testng.annotations Test BeforeClass]))

(beforeclass-ensure-admin)

(defn ^{Test {:groups ["organizations"]}} create_simple [_]
  (tasks/verify-success #(tasks/create-organization (tasks/uniqueify "auto-org") "org description")))

(defn ^{Test {:groups ["organizations" "blockedByBug-716972"]}} delete_simple [_]
  (let [org-name (tasks/uniqueify "auto-del")]
    (tasks/create-organization org-name "org to delete immediately")
    (tasks/delete-organization org-name)
    (let [remaining-org-names (doall (map :name (api/all-entities :organization)))]
      (verify (not (some #{org-name} remaining-org-names))))))

(defn ^{Test {:groups ["organizations" "validation" ]}} duplicate_disallowed [_]
  (let [org-name (tasks/uniqueify "test-dup")]
    (validate/duplicate_disallowed
     #(tasks/create-organization org-name "org-description"))))

(defn ^{Test {:groups ["organizations" "validation"]}} name_required [_]
  (validate/name-field-required #(tasks/create-organization nil "org description")))

(defn ^{Test {:groups ["organizations"]}} edit [_]
  (let [org-name (tasks/uniqueify "auto-ren")]
    (tasks/create-organization org-name "org to edit immediately")
    (tasks/edit-organization org-name :description "edited description")))

(defn valid_name [name expected-error]
  (validate/field-validation #(tasks/create-organization name "org description") expected-error))

(data-driven valid_name {Test {:groups ["organizations" "validation"]}}
             (vec (concat 
                   (validate/variations [:invalid-character :name-must-not-contain-characters])
                   (validate/variations [:trailing-whitespace :name-no-leading-trailing-whitespace]))))

(gen-class-testng)
