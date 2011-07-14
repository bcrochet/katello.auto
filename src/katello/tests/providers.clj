(ns katello.tests.providers
  (:require [katello.tasks :as tasks]
            [katello.validation :as validate]
            [katello.api-tasks :as api])
  (:import [org.testng.annotations Test BeforeGroups])
  (:use [test-clj.testng :only [gen-class-testng data-driven]]
        [com.redhat.qe.verify :only [verify]]
        [katello.tests.setup :only [beforeclass-ensure-admin]]))

(def test-provider-name (atom nil))
(def test-product-name (atom nil))

(defn test-provider [type]
  (let [result-message (tasks/create-provider
                        (tasks/uniqueify "auto-cp")
                        "my description"
                        type
                        (if (= type :redhat)
                          "http://myrepo.url.com/blah/" nil))]
    (verify (string? result-message))))

(beforeclass-ensure-admin)

(defn ^{Test {:description "Create a Red Hat provider (sunny day test)"
              :groups ["providers"]
              :enabled false}} ;;disabled, only one redhat provider allowed
  create_redhat_simple [_]
  (test-provider :redhat))

(defn ^{Test {:description "Create a custom provider (sunny day test)"
              :groups ["providers"]}} create_custom_simple [_]
  (test-provider :custom))

(defn ^{Test {:description "Delete a provider."
              :groups ["providers"]}} delete_simple [_]
  (let [cp-name (tasks/uniqueify "auto-cp-delete")]
    (tasks/create-provider
     cp-name
     "my description"
     :custom)
    (tasks/verify-success #(tasks/delete-provider cp-name))))

(defn ^{Test {:description "Change the name of a provider, and verify it can be found under its new name."
              :groups ["providers"]}}
  edit_name [_]
  (let [old-name (tasks/uniqueify "rename")
        new-name (tasks/uniqueify "newname")]
    (tasks/create-provider old-name "my description" :custom)
    (tasks/edit-provider old-name :new-name new-name)
    (tasks/navigate :named-provider-page {:cp-name new-name})
    (let [current-providers (map :name (api/all-entities :provider "ACME_Corporation"))]
      (verify (not (some #{old-name} current-providers))))))

(defn validate_provider [name description repo-url type  expected-result]
  (let [name (if (fn? name) (name) name)] ; uniqueifying at compile time defeats purpose of unique names
    (validate/field-validation       
     (fn []                           
       (tasks/create-provider name description type repo-url) 
       :success) expected-result)))

(def provider-vdata (vec (concat
                 [[nil "blah" "http://sdf.com" :redhat :name-cant-be-blank]
                    
                  ^{Test {:groups ["blockedByBug-703528"]
                          :description "Test that invalid URL is rejected."}}
                  [#(tasks/uniqueify "mytestcp") "blah" "@$#%$%&%*()[]{}" :redhat :repository-url-invalid]
                   ^{Test {:groups ["blockedByBug-703528"]
                          :description "Test that invalid URL is rejected."}}
                  [#(tasks/uniqueify "mytestcp") "blah" "https://" :redhat :repository-url-invalid]
                  [#(tasks/uniqueify "mytestcp") "blah" "@$#%$%&%*(" :redhat :repository-url-invalid]

                  [#(tasks/uniqueify "mytestcp2") "blah" nil :redhat :repository-url-cant-be-blank]
                  [#(tasks/uniqueify "mytestcp3") nil "http://sdf.com" :redhat :only-one-redhat-provider-per-org]
                  [#(tasks/uniqueify "mytestcp4") nil "http://sdf.com" :custom :success]]
                 (validate/variations
                  [#(tasks/uniqueify "mytestcp5") :javascript "http://sdf.com" :custom :success])
                 (validate/variations                  
                  [:trailing-whitespace nil  "http://sdf.com" :custom  :name-no-leading-trailing-whitespace])
                 (validate/variations
                  [:invalid-character nil "http://sdf.com" :custom :name-must-not-contain-characters]))))

(data-driven validate_provider {Test {:groups ["providers"]}} provider-vdata)

(defn ^{BeforeGroups {:description "Create a custom Provider to do further testing with."
                      :groups ["providers"]
                      :value ["products"]}}
  create_test_custom_provider [_]
  (tasks/create-provider (reset!  test-provider-name (tasks/uniqueify "cust"))
                         "my description" :custom))

(defn ^{Test {:groups ["providers" "products" "blockedByBug-712318"]
              :description "Create a product"}}
  create_product [_]
  (tasks/add-product @test-provider-name (reset! test-product-name (tasks/uniqueify "prod"))
                     "test product" "http://test.url" true true))


(gen-class-testng)