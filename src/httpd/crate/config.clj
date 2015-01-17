(ns httpd.crate.config
  (require 
  [clojure.string :as string]))

;; Convenience functions for creating apche configuration files.
;; So far, there's only functions for generating vhost configs, but
;; eventually hope to evolve to cover all apache configs.

(defn vhost-server-alias
  "Define aliases. For example if your domain-name is example.com, you
  might want to pass [\"www.example.com\" \"ftp.example.com\"]"
  [& [domain-names]]
  (if domain-names
    [(apply str "ServerAlias " (interpose " " domain-names))]
    []
    )
  )

(defn vhost-head
  [& {:keys [listening-port
             domain-name 
             server-admin-email
             aliases]
      :or {listening-port "80"}}]
  (concat
    [(str "<VirtualHost *:" listening-port ">")
     (str "ServerName " domain-name)]
    (vhost-server-alias aliases)
    [(str "ServerAdmin " server-admin-email)
     ""]
    )
  )

(def vhost-tail ["</VirtualHost>"])

(defn vhost-document-root
  [& [document-root-path]]
  (if document-root-path
    [(str "DocumentRoot \"" document-root-path "\"")
     ""]
    [])  
  )

(defn vhost-directory
  [file-path & {:keys [directory-options]
                :or {directory-options
                     ["  Order allow,deny"
                      "  Allow from all"]}}]
  (if file-path
    (into []
          (concat
            [(str "<Directory \"" file-path "\">")]
            directory-options
            ["</Directory>"
             ""]
            ))          
    []
    )
  )

(defn vhost-location
  "If path is nil, defaults to \"/\" "
  [& [path]]
  [(str "<Location " (or path "/") ">")
    "  Order allow,deny"
    "  Allow from all"
    "</Location>"
    ""])

(defn vhost-log 
  [domain-name]
  (let [log-prefix (if domain-name
                     (str domain-name "-")
                     "")]
    [(str "ErrorLog \"/var/log/apache2/" log-prefix "error\"")
      "LogLevel warn"
      (str "CustomLog \"/var/log/apache2/" log-prefix "access_log\" common")
      ""]
    )
   )

(defn vhost-rewrite-rules
  "Define the rewrite rules for a VirtualHost. Pass a vector of
  rewrite rules like this:

  :rewrite-rules [\"RewriteRule ^/$ http://test.com:8080 [P]\"
                  \"RewriteRule ^/login$ http://test.com [P]\"]
"
  [rewrite-rules 
   &{:keys [use-proxy]
        :or {use-proxy true}}]
  (if rewrite-rules
    (concat
      (if use-proxy 
        ["ProxyRequests on"]
        [])
       ["RewriteEngine on"]
      rewrite-rules
      [""])
    []
    )
  )

(defn vhost-gnutls 
  [domain-name]
  ["GnuTLSEnable on"
   "GnuTLSCacheTimeout 300"
   "GnuTLSPriorities SECURE:!VERS-SSL3.0:!MD5:!DHE-RSA:!DHE-DSS:!AES-256-CBC:%COMPAT"
   "GnuTLSExportCertificates on"
   ""
   (str "GnuTLSCertificateFile /etc/apache2/ssl.crt/" domain-name ".crts")
   (str "GnuTLSKeyFile /etc/apache2/ssl.key/" domain-name ".key")
   ""]
  )

(defn vhost-conf-default-redirect-to-https-only
  "Just redirect http request permanently to https"
  [& {:keys [domain-name 
             aliases 
             server-admin-email 
             document-root-path 
             port]
      :or {server-admin-email "your-name@your-domain.com"
           port "80"}}]
  (into 
    []
    (concat
      (vhost-head :listening-port port 
                  :domain-name domain-name 
                  :server-admin-email server-admin-email
                  :aliases aliases)
      (vhost-document-root document-root-path)
      (vhost-rewrite-rules 
        ["RewriteCond %{HTTPS} !on"
         "RewriteRule ^/(.*)$ https://%{SERVER_NAME}/$1 [R=301,L]"]
        :use-proxy false)
      vhost-tail
      )
    )
  )

(defn vhost-conf-ssl-default
  "a https default configuration"
  [& {:keys [domain-name 
             aliases 
             server-admin-email 
             document-root-path 
             port
             ssl-module]
      :or {server-admin-email "your-name@your-domain.com"
           port "443"
           ssl-module :gnutls}}]
  (into 
    []
    (concat
      (vhost-head :listening-port port
                  :domain-name domain-name 
                  :server-admin-email server-admin-email
                  :aliases aliases)
      (vhost-document-root document-root-path)
      (if (= ssl-module :gnutls)
        (vhost-gnutls domain-name)
        )        
      vhost-tail
      )
    )
  )

(defn vhost-conf-default
  "Most of my apache vhosts are for java apps. Here's what I usually use."
  [domain-name server-admin-email document-root-path aliases port]
  (into 
    []
    (concat
      (vhost-head :domain-name domain-name 
                  :server-admin-email server-admin-email
                  :aliases aliases)
      (vhost-document-root document-root-path)  
      (vhost-log domain-name)
      (vhost-location "/")
      (vhost-directory document-root-path)
      (vhost-rewrite-rules 
        [(str "RewriteRule ^/$ http://localhost:" port "/ [P]")
         (str "RewriteRule ^/(.+)$ http://localhost:" port "/$1 [P]")])
      vhost-tail
      )
    )
  )
  
(defn vhost-conf
  "Generate a vhost config. domain-name will be used to name the vhost
  conf file as well as log files. If you need to set up complicated
  vhost, pass a string of xml to :vhost-xml and you will have full
  control over what the vhost file looks like"
  [domain-name & [{:keys [server-admin-email document-root-path 
                          vhost-xml aliases port]
                   :as opts
                   :or {server-admin-email "your-name@your-domain.com"
                        port "3000"}}]]
  (or 
    vhost-xml 
    (string/join
      \newline
      (vhost-conf-default 
        domain-name 
        server-admin-email 
        document-root-path 
        aliases port)
      )
    )
  )

