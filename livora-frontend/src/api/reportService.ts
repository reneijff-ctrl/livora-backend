import apiClient from './apiClient';
import { CreateReportRequest, Report, ReportStatus, UpdateReportRequest } from '../types/report';

const reportService = {
  submitReport: async (request: CreateReportRequest): Promise<Report> => {
    const response = await apiClient.post<Report>('/reports', request);
    return response.data;
  },

  getReports: async (status?: ReportStatus, page = 0, size = 20): Promise<{ content: Report[], totalPages: number, totalElements: number }> => {
    const response = await apiClient.get<any>('/admin/reports', {
      params: { status, page, size }
    });
    return response.data;
  },

  updateReport: async (id: string, request: UpdateReportRequest): Promise<Report> => {
    const response = await apiClient.patch<Report>(`/admin/reports/${id}`, request);
    return response.data;
  }
};

export default reportService;
