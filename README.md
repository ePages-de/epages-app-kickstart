# epages-app-kickstart

This is an example implementation of the partner side SSO flow, as it is specified in the ePages6 platform.

## Development installation
### TODO

## "Production mode" installation
To install and run this example, this Ansible playbook can be used (mind the branch name):
https://github.com/ePages-de/epages-infra/blob/AD-7957-include-sso-tests/ansible/deploy-sso-partner-mock.yml
It was tested on a CentOS 7 VM.

In order to run it:

1. The target machine needs to be entered in the inventory.
For example in the one called `vagrant`, for development on localhost, or in the one called `vsphere` otherwise...
See https://github.com/ePages-de/epages-infra/blob/AD-7957-include-sso-tests/ansible/inventory/vagrant

2. The `clientId` and `clientSecret` must be configured in the configuration file of the example.
The Ansible playbook uses this template: https://github.com/ePages-de/epages-infra/blob/AD-7957-include-sso-tests/ansible/roles/sso-partner-mock/templates/config.json
Just change the values there, as soon as you have created the respective credentials on the epagesj side,
e.g. by creating a Private App.

Then the example can be started like this:
`ansible-playbook -i inventory/vagrant --limit <your-machine-fqdn-or-localhost> deploy-sso-partner-mock.yml`

Then you can access the entry page of the example:
http://<your-machine-fqdn-or-localhost>:8080/sso-partner/get-shop.html
