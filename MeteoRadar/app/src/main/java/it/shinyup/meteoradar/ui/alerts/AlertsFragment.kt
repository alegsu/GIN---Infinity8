package it.shinyup.meteoradar.ui.alerts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import it.shinyup.meteoradar.databinding.FragmentAlertsBinding

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AlertsViewModel by viewModels()
    private lateinit var adapter: AlertAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AlertAdapter { alert ->
            viewModel.markAsRead(alert.id)
        }

        binding.rvAlerts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AlertsFragment.adapter
        }

        binding.btnMarkAllRead.setOnClickListener {
            viewModel.markAllAsRead()
        }

        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.alerts.observe(viewLifecycleOwner) { alerts ->
            adapter.submitList(alerts)
            binding.tvEmpty.visibility = if (alerts.isEmpty()) View.VISIBLE else View.GONE
            binding.rvAlerts.visibility = if (alerts.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            binding.tvUnreadCount.text = if (count > 0) "$count non lette" else "Nessun allarme non letto"
            binding.btnMarkAllRead.visibility = if (count > 0) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
