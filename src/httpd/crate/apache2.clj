; Copyright (c) Michael Jerger, Dave Paroulek. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.
;
;; Apache2 Pallet Actions 

(ns httpd.crate.apache2
  (:require 
    [clojure.string :as string]
    [pallet.actions :as actions]
    [pallet.api :as api :refer [plan-fn]]
    [pallet.crate :refer [assoc-settings 
                          defplan
                          get-settings]]
    [pallet.stevedore :as stevedore]
    [httpd.crate.vhost :as vhost]
    [httpd.crate.cmds :as cmds]
    [httpd.crate.config :as config]
))

;; Apache2 related pallet actions

(defn configure-file
  "Create and upload a config file"
  [file-name content]
  (actions/remote-file
    file-name
    :owner "root"
    :group "root"
    :mode "644"
    :force true
    :literal true
    :content 
    (string/join
      \newline
      content)))

(defn configure-file-and-enable
  "Create, upload and enable an apache2 conf file"
  [conf-file-name content]
  (configure-file (str "/etc/apache2/conf-available/" conf-file-name) 
                  content)
  (cmds/a2enconf conf-file-name))

(defn config-apache2-production-grade
   [ & {:keys [limits 
               security 
               ports]
        :or {limits config/limits
             security config/security
             ports config/ports}}]
   (configure-file-and-enable 
     "/etc/apache2/conf-available/limits.conf"
     "/etc/apache2/conf-enabled/limits.conf"
     limits)
   (configure-file-and-enable 
     "/etc/apache2/conf-available/security.conf"
     "/etc/apache2/conf-enabled/security.conf"
     security)  
   (configure-file 
     "/etc/apache2/ports.conf" ports)
   (pallet.actions/exec
       {:language :bash}
       (stevedore/script
         ("a2enmod headers")
         ))
   )

(defplan install-apache2
  "Install apache2 package."
  [{:keys [instance-id]}]
  (let [settings (get-settings :httpd {:instance-id instance-id})]
    (actions/package "apache2")))

(defplan install-apachetop
  "Install apachetop package."
  [{:keys [instance-id]}]
  (let [settings (get-settings :httpd {:instance-id instance-id})]
    (actions/package "apachetop")))

(defn deploy-site
  "Deploy simple static index.html site to apache2. TODO: update this
  to be used to deploy any type of site"
  [remote-dir-path]
  ;; ensure the directory is created
  (pallet.actions/directory remote-dir-path)
  ;; create index.html
  (let [content (str "<html>"\newline
                     "<head><title>New Site</title></head>"\newline
                     "<body><div>Hello, World</div></body>"\newline
                     "</html>")]
    (pallet.actions/remote-file (str remote-dir-path "/index.html")
                                :owner "root"
                                :group "root"
                                :mode "644"
                                :content content)))

(defn configure-and-enable-vhost
  [vhost-name vhost-content]
  (let [file-avail-name 
        (str "/etc/apache2/sites-available/" vhost-name ".conf")]
    (configure-file file-avail-name vhost-content)
    (cmds/a2ensite vhost-name)))

(def ^{:dynamic true} *default-settings*
  {})

(defplan settings
  "TODO: other crates merge settings like this so it's possible to
  control aspects of actions via lein-crate. This is here so we can
  use in the future, but right now, settings are not used for
  anything yet

  Set options for installing and configuring the apache crate"
  [{:keys [instance-id] :as settings}]
  (assoc-settings
   :httpd (merge *default-settings* settings) {:instance-id instance-id}))

(defn server-spec
  "Options: (TODO: none yet)"
  [settings & {:keys [instance-id] :as options}]
  (api/server-spec
   :phases {:settings (plan-fn (httpd.crate.apache2/settings 
                                (merge settings options)))
            :bootstrap (plan-fn (install-apache2 options))
            :restart (plan-fn (cmds/apache2ctl "restart"))}
   :default-phases [:install]))
