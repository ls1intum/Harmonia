import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";

export type SortColumn = 'name' | 'commits' | 'cqi';

interface SortableHeaderProps {
    column: SortColumn;
    label: string;
    sortColumn: SortColumn | null;
    sortDirection: 'asc' | 'desc';
    handleHeaderClick: (column: SortColumn) => void;
}

export const SortableHeader = ({
                                   column,
                                   label,
                                   sortColumn,
                                   sortDirection,
                                   handleHeaderClick,
                               }: SortableHeaderProps) => {
    const isActive = sortColumn === column;

    return (
        <button
            onClick={() => handleHeaderClick(column)}
            className="flex items-center gap-2 hover:text-primary transition-colors"
        >
            {label}

            {isActive ? (
                sortDirection === "asc" ? (
                    <ArrowUp className="h-4 w-4 text-primary" />
                ) : (
                    <ArrowDown className="h-4 w-4 text-primary" />
                )
            ) : (
                <ArrowUpDown className="h-4 w-4" />
            )}
        </button>
    );
};
