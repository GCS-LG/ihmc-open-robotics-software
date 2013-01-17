% Produced by CVXGEN, 2013-01-17 22:23:42 +0000.
% CVXGEN is Copyright (C) 2006-2012 Jacob Mattingley, jem@cvxgen.com.
% The code in this file is Copyright (C) 2006-2012 Jacob Mattingley.
% CVXGEN, or solvers produced by CVXGEN, cannot be used for commercial
% applications without prior written permission from Jacob Mattingley.

% Filename: cvxsolve.m.
% Description: Solution file, via cvx, for use with sample.m.
function [vars, status] = cvxsolve(params, settings)
if isfield(params, 'Psi_k_1')
  Psi_k_1 = params.Psi_k_1;
elseif isfield(params, 'Psi_k')
  Psi_k_1 = params.Psi_k{1};
else
  error 'could not find Psi_k_1'
end
if isfield(params, 'Psi_k_2')
  Psi_k_2 = params.Psi_k_2;
elseif isfield(params, 'Psi_k')
  Psi_k_2 = params.Psi_k{2};
else
  error 'could not find Psi_k_2'
end
epsilon = params.epsilon;
if isfield(params, 'eta_d_1')
  eta_d_1 = params.eta_d_1;
elseif isfield(params, 'eta_d')
  eta_d_1 = params.eta_d{1};
else
  error 'could not find eta_d_1'
end
if isfield(params, 'eta_d_2')
  eta_d_2 = params.eta_d_2;
elseif isfield(params, 'eta_d')
  eta_d_2 = params.eta_d{2};
else
  error 'could not find eta_d_2'
end
if isfield(params, 'etamax_1')
  etamax_1 = params.etamax_1;
elseif isfield(params, 'etamax')
  etamax_1 = params.etamax{1};
else
  error 'could not find etamax_1'
end
if isfield(params, 'etamax_2')
  etamax_2 = params.etamax_2;
elseif isfield(params, 'etamax')
  etamax_2 = params.etamax{2};
else
  error 'could not find etamax_2'
end
if isfield(params, 'etamin_1')
  etamin_1 = params.etamin_1;
elseif isfield(params, 'etamin')
  etamin_1 = params.etamin{1};
else
  error 'could not find etamin_1'
end
if isfield(params, 'etamin_2')
  etamin_2 = params.etamin_2;
elseif isfield(params, 'etamin')
  etamin_2 = params.etamin{2};
else
  error 'could not find etamin_2'
end
if isfield(params, 'kappa_k_1')
  kappa_k_1 = params.kappa_k_1;
elseif isfield(params, 'kappa_k')
  kappa_k_1 = params.kappa_k{1};
else
  error 'could not find kappa_k_1'
end
if isfield(params, 'kappa_k_2')
  kappa_k_2 = params.kappa_k_2;
elseif isfield(params, 'kappa_k')
  kappa_k_2 = params.kappa_k{2};
else
  error 'could not find kappa_k_2'
end
cvx_begin
  % Caution: automatically generated by cvxgen. May be incorrect.
  variable eta_1(3, 1);
  variable eta_2(3, 1);

  minimize(quad_form(Psi_k_1*eta_1 - kappa_k_1, eye(3)) + quad_form(epsilon*(eta_1 - eta_d_1), eye(3)) + quad_form(Psi_k_2*eta_2 - kappa_k_2, eye(3)) + quad_form(epsilon*(eta_2 - eta_d_2), eye(3)));
  subject to
    etamin_1 <= eta_1;
    etamin_2 <= eta_2;
    eta_1 <= etamax_1;
    eta_2 <= etamax_2;
cvx_end
vars.eta_1 = eta_1;
vars.eta{1} = eta_1;
vars.eta_2 = eta_2;
vars.eta{2} = eta_2;
status.cvx_status = cvx_status;
% Provide a drop-in replacement for csolve.
status.optval = cvx_optval;
status.converged = strcmp(cvx_status, 'Solved');
